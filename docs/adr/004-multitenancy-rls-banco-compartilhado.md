# ADR-004: Multi-tenancy com RLS e banco compartilhado

- **Data**: 2026-09-18
- **Status**: Aceito
- **Decisores**: Andrey Dedey
- **Tags**: arquitetura, multi-tenancy, banco-de-dados, segurança

## Contexto e Problema

A plataforma é multi-institucional: cada instituição é um inquilino isolado cujos
dados não devem ser acessíveis por outra. O PRD 01 exige que "dado que estou
autenticado como gestor da instituição A, quando navego pela plataforma, então vejo
somente dados da instituição A", e que tentativas de acesso direto a registros de
outra instituição sejam negadas sem revelar informação.

Precisamos definir como esse isolamento será implementado no banco e na aplicação,
de forma que:

- Seja impossível vazar dados entre instituições por bug no código Java
- Os repositories JPA fiquem limpos, sem filtros manuais por `institutionId`
- As rotas da API não precisem carregar `institutionId` em todo path

## Fatores de Decisão

- O projeto usa PostgreSQL (ADR-001), que oferece Row-Level Security nativo
- O volume de dados é baixo (dezenas de instituições, não milhares) — schema
  separado por tenant seria overengineering
- A equipe é pequena e o risco de esquecer um `WHERE institution_id = ?` em alguma
  query é real; a filtragem precisa ser automática
- O isolamento precisa funcionar antes mesmo da autenticação ser implementada
  (PRD 01 vem depois)

## Opções Consideradas

- **A — RLS com banco compartilhado**: todas as tabelas em um schema, PostgreSQL
  filtra via policy
- **B — Filtro manual na aplicação**: toda query inclui `WHERE institution_id = ?`
  via Spring Data JPA specifications ou similar
- **C — Schema por tenant**: cada instituição tem seu próprio schema no PostgreSQL

## Decisão

Escolhemos a **Opção A** — Row-Level Security com banco compartilhado.

### Como funciona

1. O frontend armazena a instituição ativa em contexto (`InstitutionContext`) e
   envia o header `X-Institution-Id` em toda requisição que opera sobre dados
   de tenant (laboratórios, equipamentos, medições, etc.)

2. Um servlet filter no Spring intercepta o header e executa na conexão JDBC:
   ```sql
   SET LOCAL app.current_institution = '<uuid>';
   ```
   `SET LOCAL` tem escopo de transação — é descartado automaticamente no final
   do request.

3. Tabelas com dados de tenant (ex: `laboratory`) têm RLS habilitado com policy:
   ```sql
   ALTER TABLE laboratory ENABLE ROW LEVEL SECURITY;

   CREATE POLICY laboratory_institution_isolation ON laboratory
     USING (institution_id = current_setting('app.current_institution')::uuid);
   ```

4. O usuário JDBC da aplicação **não é superuser** (superusers ignoram RLS).

### Tabelas sem RLS

A tabela `institution` não tem RLS — ela precisa ser listada sem filtro de tenant
para que o seletor de instituição funcione. O controle de quais instituições cada
usuário pode ver será feito na service layer quando a autenticação for implementada.

Tabelas globais como `emission_factor` (PRD 09) também não terão RLS.

### Requisições sem header

- Endpoints de instituição (`/api/v1/institutions`) não exigem o header
- Endpoints de dados de tenant (`/api/v1/laboratories`, etc.) retornam
  `400 Bad Request` se o header estiver ausente

### Consequências Positivas

- Isolamento garantido no banco — um bug no Java não vaza dados entre tenants
- Repositories JPA ficam limpos, sem filtros manuais
- Rotas simplificadas: `/api/v1/laboratories` em vez de
  `/api/v1/institutions/{id}/laboratories`
- Funciona antes da autenticação existir (o header faz o papel do tenant ID)
- Adicionar novas tabelas ao isolamento é mecânico: habilitar RLS + criar policy

### Consequências Negativas

- RLS não funciona em H2 — testes de integração exigem PostgreSQL real
  (Testcontainers)
- O `SET LOCAL` precisa ser executado antes de qualquer query na transação;
  um filter mal configurado pode causar acesso sem filtro
- Debug mais difícil: queries no pgAdmin ou ferramentas externas precisam
  setar a variável manualmente para ver dados filtrados
- Superusers ignoram RLS — o usuário de deploy/migração (que precisa ser
  superuser para criar policies) não deve ser o mesmo da aplicação

## Links

- [ADR-001 — Stack](./001-stack-spring-boot-react-vite-bun-shadcn-postgres.md)
- [PostgreSQL RLS docs](https://www.postgresql.org/docs/current/ddl-rowsecurity.html)
