# TDD — Fatores de Emissão

| Campo            | Valor                                          |
| ---------------- | ---------------------------------------------- |
| Tech Lead        | Andrey Dedey                                   |
| PRD de origem    | `docs/prds/09-fatores-de-emissao.md`           |
| ADRs relevantes  | ADR-001 (stack), ADR-004 (multi-tenancy RLS)   |
| Status           | Draft                                          |
| Criado em        | 2026-10-01                                     |
| Atualizado em    | 2026-10-01                                     |

---

## Contexto

O fator de emissão é o multiplicador que converte energia consumida (kWh) em carbono equivalente emitido (kgCO₂). No Brasil, ele é calculado mensalmente pelo MCTI para o Sistema Interligado Nacional (SIN) e publicado com atraso de alguns meses. Sem o fator do mês correto, o cálculo de emissões não pode ser executado.

A maior parte do backend foi construída durante o PRD 06 (Cálculo de Emissões) como pré-requisito direto: a entidade `EmissionFactor`, o controller, o service e as migrations V16/V18 já existem e estão em produção. O que falta é a especificação formal, os testes e uma melhoria de UX na interface — a **detecção de lacunas** (meses sem fator no ano corrente).

### O que existe hoje

O branch `main` contém:

- `EmissionFactor` — entidade institution-scoped com `referenceMonth` (YearMonth → DATE), `value` (NUMERIC 10,6), `source` (VARCHAR 500)
- `EmissionFactorRepository` — queries para existência por mês, listagem filtrada por intervalo de YearMonth
- `EmissionFactorService` — CRUD com validações: mês obrigatório, valor > 0, fonte obrigatória, duplicata bloqueada (409)
- `EmissionFactorController` — POST/PUT/DELETE com `@PreAuthorize("hasRole('ADMIN')")`, GET público, rota base `/emission-factors`
- DTOs: `CreateEmissionFactorRequest(referenceMonth, value, source)` e `EmissionFactorDTO(id, referenceMonth, value, source)`
- V16: tabela original global (year + month columns). V18: refatoração para `reference_month DATE`, adição de `institution_id` com RLS, novo UNIQUE `(institution_id, reference_month)`
- Frontend `EmissionFactorsPage.tsx`: tabela com formulário inline (CRUD), filtro por ano, colunas Ano/Mês/Valor/Fonte/Ações
- `client/src/lib/api/emission-factors.ts`: funções `listEmissionFactors`, `createEmissionFactor`, `updateEmissionFactor`, `deleteEmissionFactor`
- `client/src/lib/schemas/emissionFactorSchema.ts`: schema Zod com validação de ano (>= 2010), mês (1–12), valor (> 0), fonte obrigatória

### Decisões resolvidas

- **SIN apenas — sistema isolado removido do escopo.** O TCC foca em IFPAs e universidades públicas da região de Belém, todas conectadas ao SIN. Sistemas isolados (Amazônia profunda) foram identificados como fora do contexto real das instituições cadastradas. Adicioná-los criaria um campo de tipo de sistema elétrico sem utilidade prática e sem dados para validar. Se o escopo futuro incluir instituições isoladas, uma migration `ADD COLUMN` com um novo tipo de fator bastará — a decisão não destrói a extensibilidade.
- **Granularidade mensal — fator anual removido.** O PRD original citava fatores anuais, mas o MCTI publica dados mensais e o estudo FACOMP base usou granularidade mensal. O sistema usa `YearMonth referenceMonth` internamente e `DATE` (primeiro dia do mês) no banco. Fator "anual" seria uma aproximação mais grosseira que os dados disponíveis.
- **Scopo por instituição via RLS.** A modelagem original era global (um único fator para toda a plataforma). A V18 migrou para institution-scoped: cada instituição gerencia seus próprios fatores. Isso preserva a rastreabilidade por instituição e possibilita que uma instituição corrija/atualize seu fator sem afetar as demais.
- **Escrita restrita a ROLE_ADMIN.** POST/PUT/DELETE exigem `ROLE_ADMIN`. GET é aberto (qualquer usuário autenticado pode consultar). Gestores podem ver os fatores usados nos seus cálculos, mas não os alterar.
- **Imutabilidade de instantâneos via design de cálculo.** O critério de reprodutibilidade (fator corrigido não altera cálculo passado) é garantido pelo fato de que `EmissionResult` persiste `emissionFactor` com o valor no momento do cálculo — não uma FK para o `EmissionFactor`. Não há lógica adicional necessária neste PRD.
- **Detecção de lacunas no frontend.** A API retorna a lista de fatores existentes. O frontend calcula os meses "Pendente" (meses ≤ data atual sem fator) e "Em uso" (fator mais recente ≤ hoje) sem endpoint adicional. Essa decisão evita um endpoint de diff ad-hoc e mantém o backend simples.

## Definição do Problema

O sistema calcula emissões (PRD 06) e precisa do fator de emissão de cada mês abrangido pelo período letivo. A ausência do fator bloqueia o cálculo. O que está incompleto:

1. **Não há especificação formal** — sem spec, sem rastreabilidade dos critérios de aceite
2. **Não há testes** — nenhum teste de integração ou unitário cobre a feature
3. **A interface não mostra lacunas** — a página lista apenas fatores existentes; o ADMIN não tem visibilidade de quais meses ainda precisam ser cadastrados

**O que acontece se não resolvermos o item 3:**
O administrador precisa conferir mês a mês manualmente se falta algum fator para o período letivo corrente — risco de cálculo bloqueado em produção sem aviso prévio.

## Escopo

### Dentro do escopo

- CRUD de fatores de emissão mensais (já implementado — a testar)
- Fator por mês/ano de referência, scoped por instituição via RLS
- Validação: mês obrigatório, valor > 0, fonte obrigatória
- Bloqueio de duplicata: mesmo `(institution_id, reference_month)` → 409
- Listagem com filtro por ano (paginação)
- Controle de acesso: escrita somente ADMIN, leitura aberta
- **Detecção de lacunas no frontend**: meses sem fator no ano selecionado marcados como "Pendente"
- **Status visual do fator**: "Em uso" (mais recente ≤ hoje), "Anterior" (demais registrados), "Pendente" (meses faltantes ≤ hoje)
- Especificação formal + testes de integração + testes unitários

### Fora do escopo

- Sistema isolado (off-grid): removido; SIN único
- Fatores anuais: removido; granularidade mínima é mensal
- Coleta automática do site do MCTI
- Fatores de outros países, estados ou distribuidoras
- Fatores de escopo 1 ou escopo 3
- Cálculo próprio de fator a partir da matriz elétrica
- Endpoint dedicado de "gaps" no backend (cálculo feito no frontend)

---

## Solução Técnica

### Visão geral da arquitetura

```
┌─────────────────────────────────────────────────────────┐
│  Frontend (React + Vite + shadcn/ui)                    │
│                                                         │
│  EmissionFactorsPage: tabela com status de cobertura    │
│  Linhas "Pendente" geradas no frontend (sem API)        │
│  Status: Em uso / Anterior / Pendente                   │
│                                                         │
│  Zod schema  ──→  React Hook Form  ──→  formulário      │
│  inline (add / edit rows)                               │
└──────────────────────────┬──────────────────────────────┘
                           │ HTTP JSON
                           │ Header: X-Institution-Id
┌──────────────────────────▼──────────────────────────────┐
│  Backend (Spring Boot)                                  │
│                                                         │
│  EmissionFactorController  ──→  EmissionFactorService   │
│                                                         │
│  Validação: mês, valor > 0, fonte, duplicata            │
│  RLS via TenantContext (SET app.current_institution)    │
└──────────────────────────┬──────────────────────────────┘
                           │ JDBC
┌──────────────────────────▼──────────────────────────────┐
│  PostgreSQL + RLS                                       │
│  emission_factor                                        │
│  UNIQUE (institution_id, reference_month)               │
│  RLS policy por institution_id                          │
└─────────────────────────────────────────────────────────┘
```

### Modelo de dados

**Tabela `emission_factor`** (estado atual, após V16 + V18)

| Coluna             | Tipo                 | Restrições                                  |
| ------------------ | -------------------- | ------------------------------------------- |
| `id`               | `UUID`               | PK, gerado automaticamente                 |
| `reference_month`  | `DATE`               | NOT NULL — armazena o primeiro dia do mês   |
| `value`            | `NUMERIC(10, 6)`     | NOT NULL                                    |
| `source`           | `VARCHAR(500)`       | NOT NULL                                    |
| `institution_id`   | `UUID`               | FK → institution(id), NOT NULL              |

**Constraints:**
- `UNIQUE (institution_id, reference_month)` — um fator por mês por instituição
- `RLS policy`: `institution_id = current_setting('app.current_institution', true)::uuid`

**Índices:**
- `idx_emission_factor_institution_id` — filtro RLS e listagem
- `idx_emission_factor_reference_month` — filtro por intervalo de datas

**Sem migrations adicionais.** A modelagem atual (V18) já está correta para o escopo definido.

### API REST

| Método   | Rota                         | Descrição                            | Status | Permissão  |
| -------- | ---------------------------- | ------------------------------------ | ------ | ---------- |
| `POST`   | `/api/v1/emission-factors`   | Cadastrar fator do mês               | 201    | ADMIN      |
| `GET`    | `/api/v1/emission-factors`   | Listar fatores (paginado, filtro ano) | 200    | autenticado|
| `PUT`    | `/api/v1/emission-factors/{id}` | Atualizar fator                   | 200    | ADMIN      |
| `DELETE` | `/api/v1/emission-factors/{id}` | Remover fator                     | 204    | ADMIN      |

**Parâmetros de listagem:**
- `?year=2025` — filtra pela parte ano de `reference_month`; sem parâmetro = todos os registros
- `?page=0&size=24&sort=referenceMonth,desc` (padrão)

**Contrato — POST e PUT:**

```json
// Request
{
  "referenceMonth": "2025-06",
  "value": 0.048012,
  "source": "MCTI — Fatores de Emissão de CO₂ do SIN, ciclo 2025"
}

// Response 201 / 200
{
  "id": "aaa-...",
  "referenceMonth": "2025-06",
  "value": 0.048012,
  "source": "MCTI — Fatores de Emissão de CO₂ do SIN, ciclo 2025"
}
```

**Serialização de `YearMonth`:** o campo `referenceMonth` é serializado/desserializado como `"YYYY-MM"` via `YearMonthAttributeConverter` (JPA) e configuração Jackson do projeto.

**Regras de negócio:**
- `POST` com `(institution_id, reference_month)` já existente → `409 Conflict` com mensagem indicando o mês duplicado
- `POST/PUT` sem `referenceMonth` → `400 Bad Request`
- `POST/PUT` com `value <= 0` → `400 Bad Request`
- `POST/PUT` com `source` em branco → `400 Bad Request`
- `DELETE` de ID inexistente → `404 Not Found`

### Backend — Entidades e repositórios

**`EmissionFactor`** — não requer alterações. Campos atuais: `id`, `referenceMonth` (YearMonth com `YearMonthAttributeConverter`), `value`, `source`, `institution` (ManyToOne LAZY).

**`EmissionFactorRepository`** — não requer alterações. Métodos atuais:

```java
boolean existsByReferenceMonth(YearMonth referenceMonth);
boolean existsByReferenceMonthAndIdNot(YearMonth referenceMonth, UUID id);
Page<EmissionFactor> findByReferenceMonthBetween(YearMonth start, YearMonth end, Pageable pageable);
```

**`EmissionFactorService`** — não requer alterações. Método `getOrThrow(UUID id)` está com visibilidade de pacote (intencional — usado por `EmissionCalculationService`).

**`EmissionFactorController`** — não requer alterações.

### Backend — Exceções

Já registradas em `GlobalExceptionHandler`:

| Exceção                           | HTTP   |
| --------------------------------- | ------ |
| `EmissionFactorNotFoundException` | 404    |
| `DuplicateEmissionFactorException`| 409    |
| `InvalidEmissionFactorException`  | 400    |

### Frontend — Detecção de lacunas

**O que muda:** a `EmissionFactorsPage.tsx` atualmente lista apenas registros existentes. O novo comportamento inclui linhas "Pendente" para meses faltantes.

**Algoritmo (computado no cliente):**

```
1. Receber a lista de fatores do ano selecionado (ou ano corrente por padrão)
2. Determinar o mês máximo a checar: min(mês corrente, dezembro do ano selecionado)
3. Para cada mês de janeiro até o mês máximo:
   a. Se existe fator → status "Em uso" ou "Anterior"
   b. Se não existe → gerar entrada sintética com status "Pendente"
4. "Em uso": o fator mais recente com referenceMonth <= hoje
5. "Anterior": todos os outros fatores cadastrados (mais velhos que "Em uso")
6. Ordenar: do mais recente para o mais antigo, intercalando Pendentes
```

**Status visuais:**

| Status     | Cor           | Condição                                           |
| ---------- | ------------- | -------------------------------------------------- |
| Em uso     | verde (green) | Fator mais recente com `referenceMonth` ≤ hoje     |
| Anterior   | cinza (muted) | Fatores mais antigos que "Em uso"                  |
| Pendente   | âmbar (amber) | Meses ≤ hoje sem fator cadastrado no ano selecionado|

**Linhas Pendente não têm ações de editar/remover** — apenas o botão "Cadastrar" que abre o formulário inline pré-preenchido com o mês correspondente.

**Tipos TypeScript adicionados em `emission-factors.ts`:**

```typescript
export type EmissionFactorStatus = 'em-uso' | 'anterior' | 'pendente'

export type EmissionFactorRow =
  | (EmissionFactor & { status: 'em-uso' | 'anterior' })
  | { status: 'pendente'; referenceMonth: string; id?: never }
```

**Schema Zod** — sem alterações. `emissionFactorFormSchema` já valida ano ≥ 2010, mês 1–12, valor > 0, fonte obrigatória.

**Colunas da tabela atualizada:**

| Coluna                | Conteúdo                                        |
| --------------------- | ----------------------------------------------- |
| Competência           | `MMM/YYYY` — ex: "Out/2025"                     |
| Status                | Badge colorido: Em uso / Anterior / Pendente    |
| Valor (kgCO₂/kWh)     | Valor numérico (6 casas decimais) ou "—"        |
| Fonte                 | Texto truncado ou "—"                           |
| Ações                 | Editar + Remover (existentes) / Cadastrar (pendentes) |

**Navegação:** sem alterações; a rota `/emission-factors` já existe na sidebar.

---

## Riscos

| Risco | Impacto | Probabilidade | Mitigação |
|-------|---------|---------------|-----------|
| Linhas Pendente confundem usuário como erros | Médio | Média | Badge âmbar com label explícito "Pendente" + tooltip "Mês sem fator cadastrado" |
| ADMIN cadastra fator para mês futuro por engano | Baixo | Baixa | Aceitar: fator futuro é válido e pode ser cadastrado com antecedência |
| Muitos meses Pendente (fator nunca cadastrado) → tabela poluída | Médio | Baixa | Mostrar Pendentes apenas para o ano selecionado, não para todos os anos; padrão = ano corrente |
| Fator corrigido afeta cálculo passado silenciosamente | Alto | Baixa | Já mitigado: `EmissionResult` persiste o valor snapshot (não FK) |
| `YearMonth` serializado diferente entre testes e produção | Médio | Baixa | `YearMonthAttributeConverter` + Jackson config já testados em PRD 06; reutilizar o padrão |

---

## Plano de Implementação

A maior parte da lógica de negócio já existe. O esforço concentra-se em especificação, testes e a melhoria de UX de detecção de lacunas.

| Fase | Tarefa | Descrição | Esforço |
|------|--------|-----------|---------|
| **1 — Spec** | Spec + tasks | Escrever spec formal com histórias e critérios de aceite; criar tasks.md | baixo |
| **2 — Testes** | Testes de integração | Endpoints CRUD + RLS + duplicata + validações (Testcontainers) | médio |
| **2 — Testes** | Testes unitários | Service: validações de campo, duplicata, não encontrado | baixo |
| **3 — Frontend** | Detecção de lacunas | Adicionar lógica de status + linhas Pendente + badge na tabela | médio |
| **4 — Gate** | Verify + Audit | `onp-spec verify` + `onp-spec audit --ci` → exit 0 | — |

**Dependências:** Fase 1 desbloqueia Fase 2; Fases 2 e 3 podem rodar em paralelo.

---

## Estratégia de Testes

| Tipo | Escopo | Abordagem |
|------|--------|-----------|
| **Integração (backend)** | Endpoints REST + RLS + constraints | Requisições HTTP reais contra PostgreSQL em container (Testcontainers) |
| **Unitário (backend)** | Service | Mock dos repositories; foco nas regras de validação e duplicata |

**Cenários a testar — integração:**

- ADMIN cadastra fator com dados válidos → 201
- ADMIN cadastra fator sem `referenceMonth` → 400
- ADMIN cadastra fator com `value <= 0` → 400
- ADMIN cadastra fator com `source` em branco → 400
- ADMIN cadastra fator duplicado (mesmo mês) → 409
- ADMIN atualiza fator existente → 200
- ADMIN atualiza fator com mês de outro fator existente → 409
- ADMIN remove fator → 204
- ADMIN remove fator inexistente → 404
- RESEARCHER tenta POST → 403
- Listagem sem filtro retorna todos os fatores da instituição
- Listagem `?year=2025` retorna apenas fatores de 2025
- Instituição A não vê fatores da instituição B (RLS)

**Cenários a testar — unitários:**

- `create` com `referenceMonth` nulo → `InvalidEmissionFactorException`
- `create` com `value` zero ou negativo → `InvalidEmissionFactorException`
- `create` com `source` em branco → `InvalidEmissionFactorException`
- `create` com mês já existente → `DuplicateEmissionFactorException`
- `update` com mês de outro registro → `DuplicateEmissionFactorException`
- `delete` de ID inexistente → `EmissionFactorNotFoundException`
