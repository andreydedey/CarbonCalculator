# TDD — Acompanhamento Longitudinal

| Campo            | Valor                                                               |
| ---------------- | ------------------------------------------------------------------- |
| Tech Lead        | Andrey Dedey                                                        |
| PRD de origem    | `docs/prds/08-acompanhamento-longitudinal.md`                       |
| ADRs relevantes  | ADR-001 (stack), ADR-004 (multi-tenancy RLS)                        |
| PRDs dependentes | PRD 03 (Equipamentos), PRD 05 (Calendário), PRD 06 (Cálculo)        |
| Status           | Draft                                                               |
| Criado em        | 2026-10-01                                                          |
| Atualizado em    | 2026-10-01                                                          |

---

## Contexto

O cálculo de emissões (PRD 06) produz um resultado on-demand: re-executa toda a aritmética a cada requisição a partir dos dados cadastrados. Isso é correto para análise atual, mas impede qualquer comparação temporal — se alguém corrigir o cadastro de um equipamento depois de fechar o semestre, o número "oficial" de 2024.2 muda silenciosamente.

O acompanhamento longitudinal resolve isso com **instantâneos imutáveis**: o gestor congela explicitamente o resultado de um período letivo, que passa a compor uma série histórica auditável. Cada instantâneo preserva não só o total de emissões, mas todas as premissas que o geraram.

O design no Pencil (frame "8 – Acompanhamento") mostra uma única tela com quatro zonas: KPI cards de tendência, gráfico de barras semestral, tabela histórica e o botão "Registrar Instantâneo" como única ação de escrita.

### O que existe hoje

- `EmissionCalculationService.calculate(periodId)` — motor de cálculo on-demand, retorna `EmissionResultDTO` completo
- Nenhuma entidade de persistência de resultado existe; tudo é recomputado a cada chamada
- `AcademicPeriod` — tem `name`, `startDate`, `endDate` (fonte do label "2025.1")

### Decisões resolvidas

- **Instantâneo criado por ação explícita do usuário.** O PRD deixava em aberto criação manual vs. automática ao fim do período. O design resolve: botão "Registrar Instantâneo" (ícone câmera) no header da página. Criação automática fica para V2.

- **Instantâneos são imutáveis — apenas deleção é permitida.** O PRD cita imutabilidade como requisito central da série histórica. Se um erro real de cadastro for descoberto, o fluxo correto é: deletar o instantâneo, corrigir os dados, recalcular e registrar novamente. Não existe endpoint de atualização.

- **O instantâneo armazena o resultado completo serializado como JSON.** Para garantir auditabilidade plena das premissas (parque, fatores, ocupação), o `EmissionResultDTO` completo é persistido como `JSONB`. Os campos resumo (emissão total, energia, estações, fator médio) ficam em colunas separadas para consulta eficiente sem deserializar o JSON.

- **Fator SIN na tabela = média ponderada pela energia de cada mês do período.** O período abrange vários meses com fatores distintos. O valor exibido na coluna "FATOR SIN" é `Σ(emissão_mês) / Σ(energia_mês)` — o fator efetivo do período — calculado no momento do snapshot e persistido.

- **VARIAÇÃO relativa ao instantâneo imediatamente anterior (por data de início do período).** Comparação cronológica, não ano-a-ano. O primeiro instantâneo da série exibe "—".

- **Apenas ADMIN e MANAGER podem registrar e deletar instantâneos.** Consulta aberta a todos os papéis autenticados.

- **"Redução Possível" no KPI card é placeholder em V1.** O design mostra "–57,5% · cenário mini PCs" que depende de um cenário salvo (PRD 07). Enquanto PRD 07 não estiver implementado, o card exibe "—" com label "configure um cenário de simulação".

- **Sem paginação na tabela em V1.** O design mostra todos os registros inline. A série histórica de um TCC raramente ultrapassa 10–15 períodos; paginação fica para V2.

---

## Escopo

### Dentro do escopo

- Entidade `EmissionSnapshot` com RLS por instituição
- Criação de instantâneo a partir de um período calculado (POST)
- Listagem de instantâneos da instituição (GET)
- Detalhe de um instantâneo com premissas completas (GET)
- Deleção deliberada de instantâneo (DELETE)
- Página de acompanhamento: 4 KPI cards + gráfico de barras + tabela histórica
- Cálculo de variação entre períodos consecutivos
- Estado vazio com explicação (menos de 2 instantâneos)

### Fora do escopo

- Criação automática de instantâneo ao fim do período
- Edição de instantâneo (apenas delete)
- Comparação entre instituições
- Projeção de tendência futura
- KPI "Redução Possível" (depende de PRD 07)
- Drill-down por laboratório no gráfico (V2)
- Seletor de intervalo de datas interativo (V1 mostra todos)

---

## Solução Técnica

### Visão geral da arquitetura

```
┌──────────────────────────────────────────────────────────────┐
│  Frontend (React + Vite + shadcn/ui)                         │
│                                                              │
│  LongitudinalPage                                            │
│  ├─ KPI Trend Cards (4 cards)                                │
│  ├─ Bar Chart (Recharts — emissão por semestre)              │
│  ├─ Historical Table (semestre/estações/consumo/emissão/     │
│  │                    fator/variação)                        │
│  └─ "Registrar Instantâneo" modal (select período)           │
└──────────────────────────┬───────────────────────────────────┘
                           │ HTTP JSON / X-Institution-Id
┌──────────────────────────▼───────────────────────────────────┐
│  Backend                                                     │
│                                                              │
│  EmissionSnapshotController                                  │
│  EmissionSnapshotService                                     │
│    → chama EmissionCalculationService.calculate(periodId)    │
│    → persiste EmissionSnapshot com JSONB do resultado        │
└──────────────────────────┬───────────────────────────────────┘
                           │ JDBC
┌──────────────────────────▼───────────────────────────────────┐
│  PostgreSQL + RLS                                            │
│  emission_snapshot                                           │
│  RLS policy por institution_id                               │
└──────────────────────────────────────────────────────────────┘
```

### Modelo de dados

**Nova tabela `emission_snapshot`** (V19)

| Coluna                | Tipo                 | Restrições                                       |
| --------------------- | -------------------- | ------------------------------------------------ |
| `id`                  | `UUID`               | PK                                               |
| `institution_id`      | `UUID`               | FK → institution(id), NOT NULL                   |
| `period_id`           | `UUID`               | FK → academic_period(id), NOT NULL               |
| `period_name`         | `VARCHAR(100)`       | NOT NULL — ex: "2025.1" (denormalizado)          |
| `period_start`        | `DATE`               | NOT NULL — denormalizado para ordenação          |
| `period_end`          | `DATE`               | NOT NULL                                         |
| `total_emission_kg`   | `NUMERIC(12, 2)`     | NOT NULL                                         |
| `total_energy_kwh`    | `NUMERIC(12, 2)`     | NOT NULL                                         |
| `station_count`       | `INTEGER`            | NOT NULL                                         |
| `school_days`         | `INTEGER`            | NOT NULL                                         |
| `avg_emission_factor` | `NUMERIC(10, 6)`     | NOT NULL — média ponderada por energia            |
| `result_json`         | `JSONB`              | NOT NULL — EmissionResultDTO completo            |
| `created_at`          | `TIMESTAMP WITH TZ`  | NOT NULL                                         |

**Constraints:**
- `UNIQUE (institution_id, period_id)` — um instantâneo por período por instituição
- `RLS policy`: `institution_id = current_setting('app.current_institution', true)::uuid`

**Índices:**
- `idx_snapshot_institution_period_start` em `(institution_id, period_start DESC)` — listagem ordenada

### API REST

| Método   | Rota                              | Descrição                                      | Status | Permissão          |
| -------- | --------------------------------- | ---------------------------------------------- | ------ | ------------------ |
| `POST`   | `/api/v1/snapshots`               | Criar instantâneo a partir de um período       | 201    | ADMIN, MANAGER     |
| `GET`    | `/api/v1/snapshots`               | Listar instantâneos da instituição             | 200    | autenticado        |
| `GET`    | `/api/v1/snapshots/{id}`          | Detalhe com premissas completas (result_json)  | 200    | autenticado        |
| `DELETE` | `/api/v1/snapshots/{id}`          | Deletar instantâneo                            | 204    | ADMIN, MANAGER     |

**Contrato — POST:**

```json
// Request
{ "periodId": "uuid-do-periodo" }

// Response 201
{
  "id": "uuid",
  "periodId": "uuid",
  "periodName": "2025.1",
  "periodStart": "2025-02-01",
  "periodEnd": "2025-06-30",
  "totalEmissionKg": 7971.0,
  "totalEnergyKwh": 82516.0,
  "stationCount": 54,
  "schoolDays": 88,
  "avgEmissionFactor": 0.096600,
  "createdAt": "2026-10-01T14:30:00Z"
}
```

**Contrato — GET `/snapshots` (listagem):**

```json
[
  {
    "id": "uuid",
    "periodName": "2025.1",
    "periodStart": "2025-02-01",
    "totalEmissionKg": 7971.0,
    "totalEnergyKwh": 82516.0,
    "stationCount": 54,
    "schoolDays": 88,
    "avgEmissionFactor": 0.096600,
    "variationPct": 12.0,
    "createdAt": "2026-10-01T14:30:00Z"
  }
]
```

O campo `variationPct` é calculado pelo service em relação ao instantâneo anterior (por `period_start`). Primeiro da série retorna `null`.

**Regras de negócio:**
- `POST` com `periodId` que já tem instantâneo → 409 Conflict
- `POST` com `periodId` sem equipamentos ou sem fatores de emissão cadastrados → 422 (cálculo não pode ser executado)
- `DELETE` de instantâneo inexistente → 404

### Backend — Entidade e serviço

**`EmissionSnapshot`** — nova entidade:

```java
@Entity
class EmissionSnapshot {
    UUID id;
    @ManyToOne Institution institution;
    @ManyToOne AcademicPeriod period;
    String periodName;           // denormalizado
    LocalDate periodStart;       // denormalizado para ordenação
    LocalDate periodEnd;
    double totalEmissionKg;
    double totalEnergyKwh;
    int stationCount;
    int schoolDays;
    BigDecimal avgEmissionFactor;
    @Column(columnDefinition = "jsonb") String resultJson;
    Instant createdAt;
}
```

**`EmissionSnapshotService.create(periodId)`:**

```
1. Verificar que não existe snapshot para (institution, period) → 409 se sim
2. Chamar EmissionCalculationService.calculate(periodId)
3. Calcular avgEmissionFactor = totalEmissionKg / totalEnergyKwh
4. Calcular stationCount = soma de quantities de todos os LaboratoryEquipments do período
5. Calcular schoolDays = soma de schoolDays do PeriodSummary
6. Serializar EmissionResultDTO → JSON
7. Persistir EmissionSnapshot
```

**`EmissionSnapshotService.list()`:**

```
1. Buscar todos snapshots da instituição, ordenados por period_start DESC
2. Para cada snapshot, calcular variationPct em relação ao anterior na lista
   (i.e., índice i compara com índice i+1, que é o mais antigo)
3. Retornar lista de EmissionSnapshotDTO com variationPct
```

**`EmissionSnapshotController`** — sem `@PreAuthorize` global; POST e DELETE restringidos a ADMIN/MANAGER via anotação por método.

### Frontend

**Rota:** `/longitudinal` → `LongitudinalPage`

O sidebar item "Acompanhamento" já existe no design — aponta para `/longitudinal`.

**Biblioteca de gráfico:** Recharts (já instalada no projeto via `recharts`).

**`LongitudinalPage`** — estrutura fiel ao design:

```
Page header
  ├─ Breadcrumb: "Análise › Acompanhamento"
  ├─ Título: "Acompanhamento de Emissões"
  ├─ Badge de período: "YYYY.S – YYYY.S" (primeiro ao último snapshot)
  └─ Botão "Registrar Instantâneo" (câmera icon) → abre modal

KPI Cards (4 cards em grid)
  ├─ Emissão Atual: valor do snapshot mais recente + variação vs. anterior
  ├─ Média Semestral: média de todos os snapshots
  ├─ Menor Emissão: mínimo histórico + período de referência
  └─ Redução Possível: placeholder "—" (PRD 07)

Chart Card
  └─ BarChart (Recharts): eixo X = periodName, eixo Y = totalEmissionKg
     Barra atual em #24744D, demais em #8AB89C

Historical Table Card
  └─ Colunas: SEMESTRE / ESTAÇÕES / CONSUMO (kWh) / EMISSÃO (kg CO₂) / FATOR SIN / VARIAÇÃO
     Ordenação: mais recente primeiro
     VARIAÇÃO: ↑ em #DC2626 (aumento) / ↓ em #24744D (redução) / — para baseline

Estado vazio (< 1 snapshot)
  └─ Card centralizado explicando que a série se forma com o uso ao longo dos períodos
     + botão "Registrar primeiro instantâneo"
```

**Modal "Registrar Instantâneo":**
- Select de período (lista os `AcademicPeriod` da instituição que ainda não têm snapshot)
- Botão "Registrar" — chama `POST /snapshots`
- Em caso de 422: mostra erro "O período não está pronto para cálculo" com link para a tela de cálculo

**Tipos TypeScript:**

```typescript
interface EmissionSnapshotDTO {
  id: string
  periodId: string
  periodName: string
  periodStart: string
  totalEmissionKg: number
  totalEnergyKwh: number
  stationCount: number
  schoolDays: number
  avgEmissionFactor: number
  variationPct: number | null
  createdAt: string
}
```

---

## Riscos

| Risco | Impacto | Probabilidade | Mitigação |
|-------|---------|---------------|-----------|
| `result_json` cresce muito com muitos labs/meses | Baixo | Baixa | JSONB comprimido pelo PostgreSQL; série histórica de TCC < 15 períodos |
| Usuário deleta snapshot por engano e perde a série | Alto | Baixa | Modal de confirmação com nome do período explícito; sem undo |
| Cálculo falha no momento do snapshot (fator ausente) | Alto | Média | Retornar 422 com mensagem; usuário preenche o fator e tenta novamente |
| Migração V19 colide com PRD 07 (também usa V19) | Médio | Alta | Usar V20 se PRD 07 for mergeado primeiro; verificar numeração antes do merge |
| `avgEmissionFactor` calculado incorretamente para períodos com meses sem emissão | Baixo | Baixa | Usar `totalEmissionKg / totalEnergyKwh` — divide por 0 impossível se totalEnergyKwh > 0 |

---

## Plano de Implementação

| Fase | Tarefa | Descrição | Esforço |
|------|--------|-----------|---------|
| **1 — Spec** | Spec + tasks | Escrever spec formal com histórias e critérios de aceite; criar tasks.md | baixo |
| **2 — Backend** | Migration + entidade | V19 (ou V20): tabela `emission_snapshot` com RLS; entidade, repositório | baixo |
| **2 — Backend** | CRUD de snapshots | `EmissionSnapshotController` + `EmissionSnapshotService` (create, list, get, delete) | médio |
| **2 — Testes** | Testes de integração | CRUD (201/409/422/404/403), imutabilidade, RLS, cálculo de variação | alto |
| **2 — Testes** | Testes unitários | Service: duplicata, variationPct, avgEmissionFactor, 422 sem fator | médio |
| **3 — Frontend** | LongitudinalPage | KPI cards + Recharts bar chart + tabela histórica + modal + estado vazio | alto |
| **4 — Gate** | Verify + Audit | `onp-spec verify` + `onp-spec audit --ci` → exit 0 | — |

**Dependências:** backend desbloqueia frontend. PRD 07 não é pré-requisito (KPI "Redução Possível" é placeholder).

---

## Estratégia de Testes

| Tipo | Escopo | Abordagem |
|------|--------|-----------|
| **Integração (backend)** | CRUD + RLS + imutabilidade + variação | Testcontainers + PostgreSQL real; seed com período, equipamentos e fatores |
| **Unitário (backend)** | Service — validações e cálculos | Mocks dos repositórios; foco em variationPct, avgEmissionFactor, duplicata e 422 |

**Cenários a testar — integração:**

- ADMIN cria snapshot de período válido → 201 com campos corretos
- Cria snapshot de período que já tem snapshot → 409
- Cria snapshot de período sem fatores de emissão → 422
- Lista snapshots: retorna apenas snapshots da instituição (RLS)
- Lista snapshots: variationPct calculado corretamente entre dois períodos consecutivos
- Primeiro snapshot da série: variationPct null
- Detalhe do snapshot contém result_json não nulo
- RESEARCHER tenta POST → 403
- DELETE de snapshot existente → 204
- DELETE de snapshot inexistente → 404
- Snapshot de instituição B invisível para instituição A (RLS)

**Cenários a testar — unitários:**

- `create` com period já com snapshot → exceção de duplicata
- `create` com cálculo retornando energia zero → não lança NPE
- `list` com dois snapshots: variationPct = `(b - a) / a * 100`
- `list` com um snapshot: variationPct = null
- `avgEmissionFactor` = totalEmissionKg / totalEnergyKwh arredondado corretamente
