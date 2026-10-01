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

O cálculo de emissões (PRD 06) é on-demand: recomputa tudo a cada requisição a partir dos dados cadastrados. Isso é correto para análise do momento atual, mas não permite acompanhamento histórico — qualquer correção retroativa no cadastro altera silenciosamente os números "passados".

O acompanhamento longitudinal resolve isso com **instantâneos diários imutáveis**: um cron job captura automaticamente, a cada dia, as emissões da instituição. O dado histórico fica preservado mesmo que equipamentos ou fatores sejam corrigidos depois. A interface exibe os dados agregados com um seletor de granularidade (Diária / Semanal / Mensal / Por Período).

A captura diária é justificada porque cada dia do período letivo tem características distintas: laboratórios operam dias da semana diferentes (`LaboratorySchedule.dayOfWeek`), feriados produzem emissão zero, e a ocupação varia ao longo do período. A agregação mensal ou por período é derivada sobre esses snapshots diários, não ao contrário.

O design no Pencil (frame "8 – Acompanhamento") mostra: badge "Coleta automática diária" no header, quatro KPI cards, gráfico de barras com toggle de granularidade (Diária / Semanal / Mensal / Por Período), e tabela histórica ordenada do mais recente para o mais antigo.

### O que existe hoje

- `EmissionCalculationService.calculate(periodId)` — motor on-demand; retorna `EmissionResultDTO` com breakdown por mês e laboratório
- `LaboratorySchedule` — define quais dias da semana um laboratório opera
- `AcademicPeriod` — tem `startDate`, `endDate` e conjunto de `schoolDays` efetivos
- `EmissionFactor` — fator SIN mensal (`referenceMonth`, `value`)
- Nenhuma entidade de persistência de resultado histórico existe

### Decisões resolvidas

- **Snapshots criados automaticamente por cron diário.** A discussão entre manual (botão) e automático foi resolvida a favor do cron: o usuário não precisa lembrar de registrar — o sistema captura todos os dias dentro de um período letivo ativo. O botão "Registrar Instantâneo" do esboço anterior foi removido; o design exibe apenas um badge informativo.

- **Granularidade de captura: diária. Granularidade de exibição: configurável.** O cron persiste um registro por dia por instituição. A API agrega esses registros conforme o parâmetro `granularity` solicitado pelo frontend (diária, semanal, mensal, por período). Isso garante máxima fidelidade sem impor uma granularidade de leitura fixa.

- **Apenas dias dentro de um período letivo ativo são capturados.** Dias fora de qualquer `AcademicPeriod` não geram snapshot. Gaps entre períodos são implicitamente zero e não aparecem na série histórica.

- **Cron é idempotente.** Se o snapshot do dia já existe (captura duplicada por retry, por exemplo), o cron pula. Isso permite re-execução manual de recovery sem duplicar dados.

- **Snapshots são imutáveis — nenhum endpoint de deleção ou edição.** A série histórica deve ser confiável. Se os dados de um dia estiverem errados por falha de infraestrutura, a estratégia de correção é um endpoint administrativo de re-captura forçada (V2). Em V1, o dado do dia fica como está.

- **Fator SIN exibido = média ponderada pela energia.** Para agregações mensais/período, `avgEmissionFactor = Σ(daily_emission_kg) / Σ(daily_energy_kwh)` — o fator efetivo do intervalo.

- **VARIAÇÃO relativa ao registro imediatamente anterior na mesma granularidade.** Comparação cronológica, não ano-a-ano. O primeiro registro da série exibe "—".

- **Apenas ADMIN e MANAGER podem forçar re-captura (futuro V2).** Leitura aberta a todos os papéis autenticados.

- **"Redução Possível" no KPI card é placeholder em V1.** Depende de cenário salvo (PRD 07). Enquanto não implementado, exibe "—" com nota "configure um cenário de simulação".

---

## Escopo

### Dentro do escopo

- Entidade `EmissionSnapshot` diária com RLS por instituição
- Cron job Spring Boot (`@Scheduled`) capturando emissões do dia anterior por instituição
- API de consulta com parâmetro `granularity` (daily / weekly / monthly / period)
- Lógica de agregação no backend para cada granularidade
- Cálculo de `variationPct` entre registros consecutivos na granularidade solicitada
- Página de acompanhamento: 4 KPI cards + gráfico com toggle de granularidade + tabela histórica
- Estado vazio (sem snapshots ainda) com explicação

### Fora do escopo

- Re-captura forçada de dia passado (V2)
- Comparação entre instituições
- Projeção de tendência futura
- KPI "Redução Possível" (depende de PRD 07)
- Drill-down por laboratório no gráfico (V2)
- Notificação de falha do cron (V2 — observabilidade)
- Dados para dias fora de período letivo

---

## Solução Técnica

### Visão geral da arquitetura

```
┌──────────────────────────────────────────────────────────────┐
│  Frontend (React + Vite + shadcn/ui + Recharts)              │
│                                                              │
│  LongitudinalPage                                            │
│  ├─ KPI Cards (4 cards)                                      │
│  ├─ GranularityToggle (Diária/Semanal/Mensal/Por Período)    │
│  ├─ BarChart (Recharts — granularidade selecionada)          │
│  └─ HistoricalTable (data/estações/consumo/emissão/fator/var)│
└──────────────────────────┬───────────────────────────────────┘
                           │ GET /snapshots?granularity=...
┌──────────────────────────▼───────────────────────────────────┐
│  Backend                                                     │
│                                                              │
│  EmissionSnapshotController                                  │
│  EmissionSnapshotQueryService  ← agrega snapshots diários    │
│                                                              │
│  EmissionSnapshotCronService   ← @Scheduled diário           │
│    → calcula emissão do dia anterior por instituição         │
│    → persiste EmissionSnapshot (idempotente)                 │
└──────────────────────────┬───────────────────────────────────┘
                           │ JDBC
┌──────────────────────────▼───────────────────────────────────┐
│  PostgreSQL + RLS                                            │
│  emission_snapshot (por dia por instituição)                 │
│  RLS policy por institution_id                               │
└──────────────────────────────────────────────────────────────┘
```

### Modelo de dados

**Nova tabela `emission_snapshot`** (verificar numeração antes do merge; V19 ou V20 dependendo do estado do main)

| Coluna                  | Tipo                 | Restrições                                               |
| ----------------------- | -------------------- | -------------------------------------------------------- |
| `id`                    | `UUID`               | PK                                                       |
| `institution_id`        | `UUID`               | FK → institution(id), NOT NULL                           |
| `period_id`             | `UUID`               | FK → academic_period(id), NOT NULL                       |
| `snapshot_date`         | `DATE`               | NOT NULL — o dia representado                            |
| `day_of_week`           | `SMALLINT`           | NOT NULL — 1=Seg … 7=Dom                                 |
| `is_school_day`         | `BOOLEAN`            | NOT NULL — false se feriado ou recesso                   |
| `daily_emission_kg`     | `NUMERIC(12, 4)`     | NOT NULL                                                 |
| `daily_energy_kwh`      | `NUMERIC(12, 4)`     | NOT NULL                                                 |
| `emission_factor_value` | `NUMERIC(10, 6)`     | NOT NULL — fator SIN do mês do dia                       |
| `station_count`         | `INTEGER`            | NOT NULL — estações ativas nesse dia                     |
| `created_at`            | `TIMESTAMP WITH TZ`  | NOT NULL                                                 |

**Constraints:**
- `UNIQUE (institution_id, snapshot_date)` — um snapshot por dia por instituição
- `RLS policy`: `institution_id = current_setting('app.current_institution', true)::uuid`

**Índices:**
- `idx_snapshot_institution_date` em `(institution_id, snapshot_date DESC)` — listagem cronológica
- `idx_snapshot_institution_period` em `(institution_id, period_id, snapshot_date)` — agregação por período

### API REST

| Método | Rota                   | Descrição                                         | Status | Permissão   |
| ------ | ---------------------- | ------------------------------------------------- | ------ | ----------- |
| `GET`  | `/api/v1/snapshots`    | Listagem agregada de snapshots                    | 200    | autenticado |

**Parâmetros de query:**

| Parâmetro     | Tipo     | Padrão    | Descrição                                            |
| ------------- | -------- | --------- | ---------------------------------------------------- |
| `granularity` | `string` | `monthly` | `daily` \| `weekly` \| `monthly` \| `period`         |
| `startDate`   | `date`   | —         | Filtro de data inicial (ISO 8601)                    |
| `endDate`     | `date`   | —         | Filtro de data final (ISO 8601)                      |

**Contrato — GET `/snapshots?granularity=monthly`:**

```json
[
  {
    "label": "Out 2025",
    "startDate": "2025-10-01",
    "endDate": "2025-10-31",
    "totalEmissionKg": 1290.0,
    "totalEnergyKwh": 13350.0,
    "schoolDays": 22,
    "stationCount": 54,
    "avgEmissionFactor": 0.096600,
    "variationPct": 12.0
  }
]
```

**Contrato — GET `/snapshots?granularity=period`:**

```json
[
  {
    "label": "2025.2",
    "periodId": "uuid-do-periodo",
    "startDate": "2025-08-01",
    "endDate": "2025-12-15",
    "totalEmissionKg": 5180.0,
    "totalEnergyKwh": 53600.0,
    "schoolDays": 88,
    "stationCount": 54,
    "avgEmissionFactor": 0.096600,
    "variationPct": -8.5
  }
]
```

`variationPct` é calculado em relação ao registro anterior na mesma granularidade. Primeiro da série retorna `null`.

**Regras de negócio:**
- Granularity inválida → 400 Bad Request
- Nenhum snapshot ainda → 200 com array vazio (estado vazio tratado no frontend)

### Backend — Cron e serviço

**`EmissionSnapshotCronService.captureYesterday()`** — executado por `@Scheduled(cron = "0 0 1 * * *")`:

```
Para cada Institution:
  1. date = ontem
  2. Encontrar AcademicPeriod ativo para date (startDate ≤ date ≤ endDate) → pular se nenhum
  3. Verificar se snapshot (institution, date) já existe → pular se sim (idempotente)
  4. Obter EmissionFactor para (institution, year-month de date) → pular se ausente, logar aviso
  5. Determinar dayOfWeek de date
  6. Verificar se date é schoolDay no período (AcademicPeriod)
  7. Para cada Laboratory da institution:
     a. Verificar se LaboratorySchedule cobre dayOfWeek de date
     b. Se sim E isSchoolDay: calcular daily_energy_kwh = Σ(watts de cada LaboratoryEquipment × hoursPerDay)
  8. daily_emission_kg = daily_energy_kwh × emission_factor_value
  9. station_count = count de estações únicas ativas (LaboratoryEquipments de labs que operam no dia)
 10. Persistir EmissionSnapshot
```

**`EmissionSnapshotQueryService.list(granularity, startDate, endDate)`:**

```
1. Buscar EmissionSnapshot da instituição no intervalo, ordenados por snapshot_date
2. Agrupar por granularidade:
   - daily: um registro por snapshot_date
   - weekly: agrupar por ISO week (ano + número da semana)
   - monthly: agrupar por ano-mês
   - period: agrupar por period_id
3. Para cada grupo: somar daily_emission_kg e daily_energy_kwh;
   avgEmissionFactor = totalEmissionKg / totalEnergyKwh;
   schoolDays = count(is_school_day = true);
   stationCount = max(station_count) do grupo
4. Calcular variationPct entre grupos consecutivos (por startDate)
5. Retornar lista de SnapshotAggregateDTO
```

**`EmissionSnapshotController`** — único endpoint GET, sem restrição de papel.

### Frontend

**Rota:** `/longitudinal` → `LongitudinalPage`

**Biblioteca de gráfico:** Recharts (já instalada).

**`LongitudinalPage`** — estrutura fiel ao design atualizado:

```
Page header
  ├─ Breadcrumb: "Análise › Acompanhamento"
  ├─ Título: "Acompanhamento de Emissões"
  ├─ Badge "Coleta automática diária" (ícone clock, verde)
  └─ Badge de intervalo: "startDate – endDate" do conjunto de snapshots

KPI Cards (4 cards em grid)
  ├─ Emissão Atual: total do mês mais recente + variação vs. anterior
  ├─ Média Mensal: média dos totais mensais nos últimos 12 meses
  ├─ Menor Emissão: mínimo mensal histórico + referência de mês
  └─ Redução Possível: placeholder "—" com nota (PRD 07)

Chart Card
  ├─ GranularityToggle: Diária | Semanal | Mensal (ativo) | Por Período
  └─ BarChart (Recharts): eixo X = label do período, eixo Y = totalEmissionKg
     Barra mais recente em #24744D, demais em #8AB89C

Historical Table Card
  └─ Colunas: DATA / ESTAÇÕES / CONSUMO (kWh) / EMISSÃO (kg CO₂) / FATOR SIN / VARIAÇÃO
     Ordenação: mais recente primeiro
     VARIAÇÃO: ↑ em #DC2626 (aumento) / ↓ em #16A34A (redução) / — para baseline

Estado vazio (nenhum snapshot ainda)
  └─ Card centralizado explicando coleta automática com texto:
     "A série histórica é formada automaticamente a cada dia de aula.
      Os primeiros dados aparecerão amanhã."
```

**Toggle de granularidade:** ao alternar, o frontend re-consulta `GET /snapshots?granularity=<novo>` e re-renderiza o gráfico e a tabela. Os KPI cards sempre usam `granularity=monthly`.

**Tipos TypeScript:**

```typescript
type Granularity = 'daily' | 'weekly' | 'monthly' | 'period'

interface SnapshotAggregateDTO {
  label: string
  startDate: string
  endDate: string
  periodId?: string
  totalEmissionKg: number
  totalEnergyKwh: number
  schoolDays: number
  stationCount: number
  avgEmissionFactor: number
  variationPct: number | null
}
```

---

## Riscos

| Risco | Impacto | Probabilidade | Mitigação |
|-------|---------|---------------|-----------|
| Cron falha silenciosamente (fator SIN ausente, instituição sem período ativo) | Médio | Média | Logar aviso estruturado por instituição; gap no histórico é visível na UI |
| Volume de dados: 365 dias × N instituições × anos de operação | Baixo | Baixa | Série de TCC raramente ultrapassa 3 anos; índice por `(institution_id, snapshot_date DESC)` garante consultas O(log n) |
| Agregação semanal com semanas ISO cruzando dois meses | Baixo | Média | Usar `DATE_TRUNC('week', snapshot_date)` do PostgreSQL — comportamento consistente |
| `avgEmissionFactor` com `totalEnergyKwh = 0` (dia sem aula) | Baixo | Alta | Snapshots só são criados quando `isSchoolDay = true` e há laboratórios operando; se `totalEnergyKwh = 0`, logar e pular o dia |
| Migração colide com PRD 07 (ambos precisam de V19+) | Médio | Alta | Verificar numeração antes do merge |

---

## Plano de Implementação

| Fase | Tarefa | Descrição | Esforço |
|------|--------|-----------|---------|
| **1 — Spec** | Spec + tasks | Histórias e critérios de aceite; tasks.md | baixo |
| **2 — Backend** | Migration + entidade | Tabela `emission_snapshot` com RLS; entidade, repositório | baixo |
| **2 — Backend** | Cron de captura | `EmissionSnapshotCronService` com lógica diária; idempotência | alto |
| **2 — Backend** | API de consulta | `EmissionSnapshotController` + `EmissionSnapshotQueryService` com agregação por granularidade | alto |
| **2 — Testes** | Testes de integração | Cron (captura, idempotência, pulo sem fator/período), query por granularidade, RLS | alto |
| **2 — Testes** | Testes unitários | QueryService: agregação, variationPct, divisão por zero | médio |
| **3 — Frontend** | LongitudinalPage | KPI cards + toggle + Recharts + tabela + estado vazio | alto |
| **4 — Gate** | Verify + Audit | `onp-spec verify` + `onp-spec audit --ci` → exit 0 | — |

**Dependências:** backend (especialmente cron) desbloqueia frontend. PRD 07 não é pré-requisito.

---

## Estratégia de Testes

| Tipo | Escopo | Abordagem |
|------|--------|-----------|
| **Integração (backend)** | Cron + query + RLS | Testcontainers + PostgreSQL real; seed com período, equipamentos, fatores e snapshots pré-criados |
| **Unitário (backend)** | QueryService — agregação e cálculos | Mocks do repositório; foco em variationPct, avgEmissionFactor, divisão por zero |

**Cenários a testar — integração (cron):**

- Dia dentro de período ativo com fator disponível → snapshot criado com valores corretos
- Cron chamado duas vezes para o mesmo dia (idempotência) → segundo chamado não cria duplicata
- Dia fora de qualquer período letivo → nenhum snapshot criado
- Dia dentro de período mas fator SIN ausente para o mês → snapshot não criado, aviso logado
- Dia não-letivo (`isSchoolDay = false`) → snapshot criado com `daily_emission_kg = 0`
- Laboratório sem `LaboratorySchedule` para o `dayOfWeek` do dia → não contribui para emissão

**Cenários a testar — integração (query):**

- `granularity=monthly`: soma correta de vários dias do mesmo mês
- `granularity=period`: soma correta de dias do mesmo `period_id`
- `granularity=weekly`: agrupamento correto por ISO week
- `variationPct` com dois meses: `(b - a) / a × 100`
- Primeiro registro da série: `variationPct = null`
- RLS: snapshots de instituição B invisíveis para instituição A
- Filtro `startDate`/`endDate` exclui registros fora do intervalo

**Cenários a testar — unitários:**

- `avgEmissionFactor = totalEmissionKg / totalEnergyKwh` arredondado corretamente
- `variationPct` com dois grupos consecutivos
- `variationPct` com um único grupo → null
- Grupo com `totalEnergyKwh = 0` → `avgEmissionFactor = null` (não lança NPE)
