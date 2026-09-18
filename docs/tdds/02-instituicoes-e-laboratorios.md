# TDD — Cadastro de Instituições e Laboratórios

| Campo            | Valor                                          |
| ---------------- | ---------------------------------------------- |
| Tech Lead        | Andrey Dedey                                   |
| PRD de origem    | `docs/prds/02-instituicoes-e-laboratorios.md`  |
| ADRs relevantes  | ADR-001 (stack), ADR-002 (tema visual), ADR-003 (design como fonte de verdade), ADR-004 (multi-tenancy RLS) |
| Status           | Draft                                          |
| Criado em        | 2026-09-18                                     |
| Atualizado em    | 2026-09-18                                     |

---

## Contexto

Esta é a primeira feature de domínio do CarbonCalculatorTCC. O projeto é greenfield — existe apenas a estrutura de pastas e o scaffolding do Spring Boot e do React/Vite. Não há entidades, migrations, endpoints nem páginas implementadas.

Instituição e laboratório são as duas entidades raiz do sistema. Toda a cadeia posterior — equipamentos, medições, calendário, cálculo de emissões — depende delas. O laboratório é a unidade de agregação dos resultados: é por laboratório que a coordenação toma decisões sobre renovação de parque e distribuição de horários.

A plataforma é multi-institucional. Cada instituição é um inquilino isolado. O isolamento será implementado via **Row-Level Security (RLS)** no PostgreSQL com banco compartilhado — a aplicação define o tenant ativo na sessão do banco, e as policies filtram as linhas automaticamente. Nesta fase, o RLS será modelado e ativado, mas **não será enforçado por autenticação** — esse controle entra no PRD 01.

O escopo cobre apenas **laboratórios de ensino**, que seguem o calendário letivo. Laboratórios de pesquisa ficam para análise futura.

### Decisão: tipo de sistema elétrico adiado

O PRD menciona o tipo de sistema elétrico (SIN vs isolado) como atributo do laboratório. Optamos por **não implementar esse campo agora**: o Sistema Interligado Nacional cobre ~98% do Brasil, e os dados de fator de emissão para sistemas isolados raramente estão disponíveis. O cálculo assumirá SIN para todos os laboratórios. Quando houver demanda e dados, basta adicionar uma coluna via migration — não quebra nada.

## Definição do Problema

O estudo de referência (Corrêa, Cardoso e Kawasaki — FACOMP/UFPA) usou três laboratórios de uma única faculdade. Não existe hoje uma estrutura que permita representar essa realidade de forma genérica para qualquer instituição brasileira. Sem essa base, nenhuma das funcionalidades posteriores (equipamentos, medições, cálculo) pode ser construída.

**O que acontece se não resolvermos:**
- Nenhuma outra feature do sistema pode avançar — toda a cadeia depende dessas entidades.

## Escopo

### Dentro do escopo

- Entidades `Institution` e `Laboratory` no banco de dados com migrations Flyway
- Multi-tenancy via PostgreSQL RLS com banco compartilhado
- CRUD completo de instituições (criar, listar, editar)
- CRUD de laboratórios (criar, listar, editar, desativar)
- Bloqueio de exclusão de laboratório que já possui registros dependentes
- Soft-delete via flag `active` em laboratório
- Fluxo de criação de instituição com primeiro laboratório vinculado (conforme design)
- Páginas de listagem e formulário para instituições e laboratórios
- Seletor de instituição ativa no header da aplicação
- Validação de formulários no frontend com Zod + React Hook Form

### Fora do escopo

- Autenticação e autorização (PRD 01)
- Tipo de sistema elétrico (SIN/isolado) — adiado, ver decisão acima
- Laboratórios de pesquisa
- Hierarquia campus/faculdade/departamento
- Importação de dados de sistemas acadêmicos
- Mapa, planta ou localização física do laboratório
- Controle patrimonial

---

## Solução Técnica

### Visão geral da arquitetura

```
┌─────────────────────────────────────────────────────────┐
│  Frontend (React + Vite + shadcn/ui)                    │
│                                                         │
│  InstitutionContext  ──→  pages/  ──→  components/      │
│  (instituição ativa)     (CRUD)      (ui, seletor)      │
│                                                         │
│  Zod schemas  ──→  React Hook Form  ──→  formulários    │
└──────────────────────────┬──────────────────────────────┘
                           │ HTTP (JSON)
                           │ Header: X-Institution-Id
┌──────────────────────────▼──────────────────────────────┐
│  Backend (Spring Boot)                                  │
│                                                         │
│  Filter (seta tenant)  →  Controller  →  Service        │
│                           (REST)        (regras)        │
│                                                         │
│  Repository (JPA)  →  Entity                            │
└──────────────────────────┬──────────────────────────────┘
                           │ JDBC
                           │ SET app.current_institution
┌──────────────────────────▼──────────────────────────────┐
│  PostgreSQL + RLS                                       │
│  institution / laboratory                               │
│  Policies filtram por current_setting('app.current_..') │
└─────────────────────────────────────────────────────────┘
```

### Multi-tenancy com RLS

**Mecanismo:**

1. O frontend envia o header `X-Institution-Id` em toda requisição (exceto as de instituição em si)
2. Um servlet filter no Spring intercepta o header e executa `SET LOCAL app.current_institution = '<uuid>'` na conexão JDBC antes de cada request
3. O PostgreSQL aplica RLS policies que filtram `WHERE institution_id = current_setting('app.current_institution')::uuid`
4. No final do request, a transação é encerrada e o `SET LOCAL` é descartado automaticamente

**Vantagens:**
- A filtragem por tenant acontece no banco — impossível vazar dados entre instituições por bug no código Java
- Os repositories JPA ficam limpos, sem filtros manuais por `institutionId`
- As rotas da API ficam mais simples, sem `institutionId` no path

**Policy de exemplo:**

```sql
ALTER TABLE laboratory ENABLE ROW LEVEL SECURITY;

CREATE POLICY laboratory_institution_isolation ON laboratory
  USING (institution_id = current_setting('app.current_institution')::uuid);
```

A tabela `institution` **não tem RLS** — o CRUD de instituições é acessível sem filtro de tenant (o admin vê todas, o gestor vê as suas via lógica na service layer quando auth for implementada).

### Modelo de dados

**Tabela `institution`**

| Coluna       | Tipo                   | Restrições                |
| ------------ | ---------------------- | ------------------------- |
| `id`         | `UUID`                 | PK, gerado automaticamente|
| `name`       | `VARCHAR(255)`         | NOT NULL                  |
| `acronym`    | `VARCHAR(20)`          | NOT NULL, UNIQUE          |
| `city`       | `VARCHAR(255)`         | NULL                      |
| `state`      | `CHAR(2)`              | NOT NULL                  |
| `active`     | `BOOLEAN`              | NOT NULL, DEFAULT TRUE    |
| `created_at` | `TIMESTAMP WITH TZ`   | NOT NULL                  |
| `updated_at` | `TIMESTAMP WITH TZ`   | NOT NULL                  |

**Tabela `laboratory`**

| Coluna           | Tipo                 | Restrições                     |
| ---------------- | -------------------- | ------------------------------ |
| `id`             | `UUID`               | PK, gerado automaticamente     |
| `institution_id` | `UUID`               | FK → institution(id), NOT NULL |
| `name`           | `VARCHAR(255)`       | NOT NULL                       |
| `active`         | `BOOLEAN`            | NOT NULL, DEFAULT TRUE         |
| `created_at`     | `TIMESTAMP WITH TZ` | NOT NULL                       |
| `updated_at`     | `TIMESTAMP WITH TZ` | NOT NULL                       |

**Índices:**
- `laboratory(institution_id)` — usado pelo RLS e por queries
- `institution(acronym)` — UNIQUE

**RLS:**
- Habilitado em `laboratory`
- Policy filtra por `current_setting('app.current_institution')`
- O usuário JPA da aplicação não é superuser (superusers ignoram RLS)

### Decisões de modelagem

- **`state` validado contra as 27 UFs**: tanto no schema Zod (frontend) quanto na service layer (backend)
- **Sigla (`acronym`) editável**: o UUID é a chave primária, a sigla é campo de exibição
- **Criação de instituição inclui primeiro laboratório**: conforme o design, o formulário de criação de instituição já pede os dados do primeiro lab — a API trata isso como uma operação atômica (transação única)

### API REST

**Instituições**

| Método | Rota                       | Descrição                                      | Status |
| ------ | -------------------------- | ---------------------------------------------- | ------ |
| POST   | `/api/v1/institutions`     | Criar instituição + primeiro laboratório       | 201    |
| GET    | `/api/v1/institutions`     | Listar instituições acessíveis                 | 200    |
| GET    | `/api/v1/institutions/{id}`| Obter instituição por ID                       | 200    |
| PUT    | `/api/v1/institutions/{id}`| Atualizar instituição                          | 200    |

**Laboratórios** (filtrados automaticamente pelo RLS via header `X-Institution-Id`)

| Método | Rota                                    | Descrição                       | Status |
| ------ | --------------------------------------- | ------------------------------- | ------ |
| POST   | `/api/v1/laboratories`                  | Criar laboratório               | 201    |
| GET    | `/api/v1/laboratories`                  | Listar laboratórios             | 200    |
| GET    | `/api/v1/laboratories/{id}`             | Obter laboratório por ID        | 200    |
| PUT    | `/api/v1/laboratories/{id}`             | Atualizar laboratório           | 200    |
| PATCH  | `/api/v1/laboratories/{id}/deactivate`  | Desativar laboratório           | 200    |
| DELETE | `/api/v1/laboratories/{id}`             | Excluir (somente sem histórico) | 204    |

**Contratos principais:**

```json
// POST /api/v1/institutions
// Request — cria instituição + primeiro lab
{
  "name": "Universidade Federal do Pará",
  "acronym": "UFPA",
  "city": "Belém",
  "state": "PA",
  "laboratory": {
    "name": "LABCOMP-01"
  }
}

// Response 201
{
  "id": "550e8400-...",
  "name": "Universidade Federal do Pará",
  "acronym": "UFPA",
  "city": "Belém",
  "state": "PA",
  "active": true,
  "createdAt": "2026-09-18T10:00:00Z"
}
```

```json
// POST /api/v1/laboratories
// Header: X-Institution-Id: 550e8400-...
// Request
{
  "name": "LABCOMP-02"
}

// Response 201
{
  "id": "660e8400-...",
  "name": "LABCOMP-02",
  "active": true,
  "createdAt": "2026-09-18T10:00:00Z"
}
```

```json
// GET /api/v1/laboratories
// Header: X-Institution-Id: 550e8400-...
// Response 200
[
  {
    "id": "660e8400-...",
    "name": "LABCOMP-01",
    "active": true
  },
  {
    "id": "770e8400-...",
    "name": "LABCOMP-02",
    "active": true
  }
]
```

**Regras de negócio na API:**
- `DELETE /laboratories/{id}` retorna `409 Conflict` se o laboratório possui equipamentos ou medições vinculadas, com mensagem sugerindo desativação
- `GET /laboratories` por padrão retorna apenas os ativos; aceita query param `?active=false` para incluir inativos
- Requisições a `/laboratories` sem header `X-Institution-Id` retornam `400 Bad Request`
- Validação de regras de negócio (sigla duplicada, campos obrigatórios, UF válida) acontece na camada de serviço

### Frontend

**Contexto de instituição ativa:**
- `InstitutionContext` armazena o `institutionId` selecionado e expõe um setter
- Persistido em `localStorage` para manter entre sessões
- O `institutionId` é enviado automaticamente como header `X-Institution-Id` em toda requisição via interceptor HTTP

**Páginas (conforme design):**

| Página                  | Rota                       | Descrição                                              |
| ----------------------- | -------------------------- | ------------------------------------------------------ |
| Formulário instituição  | `/institutions/new`        | Card "Dados da Instituição" + card "Laboratório Vinculado" |
| Edição instituição      | `/institutions/:id/edit`   | Edição dos dados da instituição                        |
| Lista de laboratórios   | `/laboratories`            | Cards com nome, status e stats (conforme design)       |
| Formulário laboratório  | `/laboratories/new`        | Formulário de criação de lab adicional                 |
| Edição laboratório      | `/laboratories/:id/edit`   | Formulário de edição                                   |

**Validação de formulários:**
- Schemas Zod definem as regras de cada formulário (campos obrigatórios, tamanho máximo, UF validada contra lista de 27 estados)
- React Hook Form com `zodResolver` conecta os schemas aos formulários
- Erros de validação são exibidos inline nos campos, usando os componentes do shadcn/ui

**Componentes-chave:**
- **Seletor de instituição**: no header da aplicação, mostra nome/sigla da instituição ativa, permite trocar
- **Card de laboratório**: conforme design — nome, descrição, badge de status, stats no rodapé
- **Diálogo de desativação**: confirmação antes de desativar um laboratório
- **Bloqueio de exclusão**: ao tentar excluir um lab com histórico, exibe mensagem e oferece desativação

---

## Riscos

| Risco | Impacto | Probabilidade | Mitigação |
|-------|---------|---------------|-----------|
| RLS mal configurado pode bloquear o próprio JPA de ler dados | Alto | Média | Testar exaustivamente com Testcontainers; garantir que o usuário do banco não é superuser; ter migration que valida as policies |
| `SET LOCAL` não é executado antes de alguma query (race condition) | Alto | Baixa | O servlet filter garante a execução antes de qualquer lógica; testes de integração validam o fluxo completo |
| Modelo de dados precisa mudar quando PRDs posteriores forem implementados | Médio | Alta | Migrations versionadas (Flyway); modelo enxuto agora, colunas adicionadas via ALTER TABLE |
| Lógica de exclusão vs desativação confusa para o usuário | Médio | Média | UX clara: botão de excluir só aparece se não houver dependentes; se houver, mostra diálogo explicando a desativação |
| Frontend perde o header `X-Institution-Id` em alguma requisição | Médio | Baixa | Interceptor centralizado no HTTP client; backend retorna 400 se header estiver ausente |

---

## Plano de Implementação

| Fase | Tarefa | Descrição | Estimativa |
|------|--------|-----------|------------|
| **1 — Banco** | Migration V1 | Criar tabelas `institution` e `laboratory` | 0.5d |
| **1 — Banco** | Migration V2 — RLS | Habilitar RLS em `laboratory`, criar policies | 0.5d |
| **1 — Banco** | Entities | JPA entities | 0.5d |
| **2 — Backend** | Tenant filter | Servlet filter que lê `X-Institution-Id` e executa `SET LOCAL` | 0.5d |
| **2 — Backend** | Repositories | Spring Data JPA repositories | 0.5d |
| **2 — Backend** | DTOs + Mappers | Request/Response DTOs e mapeamento entity↔DTO | 0.5d |
| **2 — Backend** | Services | CRUD, validação na service layer, bloqueio de exclusão, soft-delete, criação atômica instituição+lab | 1d |
| **2 — Backend** | Controllers | Endpoints REST | 0.5d |
| **3 — Frontend** | Setup Biome + Zod + RHF | Instalar e configurar Biome, Zod, React Hook Form e React Router | 0.5d |
| **3 — Frontend** | HTTP client + interceptor | Configurar client HTTP com interceptor que injeta `X-Institution-Id` | 0.5d |
| **3 — Frontend** | InstitutionContext + seletor | Context + localStorage + componente seletor no header | 0.5d |
| **3 — Frontend** | Formulário de instituição | Dois cards: dados da instituição + lab vinculado (conforme design) | 1d |
| **3 — Frontend** | Lista de laboratórios | Cards com nome, status e stats (conforme design) | 1d |
| **3 — Frontend** | Formulário de laboratório | Criação/edição de lab + diálogo de desativação | 0.5d |
| **4 — Testes** | Testes de integração | Endpoints + RLS com Testcontainers | 1d |
| **4 — Testes** | Testes unitários | Services: validações, bloqueio de exclusão | 0.5d |

**Estimativa total**: ~9.5 dias úteis

**Dependências entre fases:**
- Fase 1 desbloqueia Fase 2
- Fase 2 desbloqueia Fases 3 e 4
- Fases 3 e 4 podem rodar em paralelo

---

## Estratégia de Testes

| Tipo | Escopo | Abordagem |
|------|--------|-----------|
| **Integração (backend)** | Endpoints REST + RLS + banco | Requisições HTTP reais contra PostgreSQL em container (Testcontainers) — necessário para testar RLS, que não funciona em H2 |
| **Unitário (backend)** | Services | Mock dos repositories para testar regras de negócio isoladamente |
| **Validação do modelo** | Migration + RLS | Verificar que a aplicação sobe, migrations rodam e policies estão ativas |

**Cenários críticos a testar:**

- Criar instituição com dados válidos + lab vinculado → 201 + ambos persistidos
- Criar instituição com sigla duplicada → 409 Conflict
- Criar instituição com UF inválida → 400 Bad Request
- `GET /laboratories` sem header `X-Institution-Id` → 400 Bad Request
- `GET /laboratories` com tenant A retorna apenas labs do tenant A (RLS)
- `GET /laboratories/{id}` com tenant B tentando acessar lab do tenant A → 404 (RLS esconde o registro)
- Desativar laboratório → `active = false`, continua retornando com `?active=false`
- Excluir laboratório sem dependentes → 204 + registro removido
- Excluir laboratório com dependentes → 409 Conflict + mensagem orientando desativação
