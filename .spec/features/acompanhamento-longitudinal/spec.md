# Spec: Acompanhamento longitudinal

> feature: acompanhamento-longitudinal
> status: auditada

## Contexto

Gestor de instituição precisa acompanhar como as emissões de CO₂ evoluem ao longo do tempo, com a granularidade que for mais útil (diária, semanal, mensal ou por período letivo). A interface agrega snapshots diários conforme a granularidade selecionada.

## Histórias

### US-044 — Consulta histórica de emissões por granularidade

Como gestor da instituição, quero consultar o histórico de emissões com controle de granularidade (diária, semanal, mensal, por período), para que eu identifique tendências e anomalias com o nível de detalhe adequado.

#### AC-136 — Agregação mensal soma corretamente dias do mesmo mês

- **Dado** que existem 5 snapshots diários em outubro de 2025, com `daily_emission_kg` de 100, 120, 90, 110 e 130 para a minha instituição
- **Quando** faço `GET /api/v1/snapshots?granularity=monthly`
- **Então** o registro de "Out 2025" tem `totalEmissionKg = 550` e `schoolDays = 5`

#### AC-137 — Agregação por período letivo soma todos os dias do período

- **Dado** que existem snapshots diários distribuídos ao longo do período "2025.2" para a minha instituição
- **Quando** faço `GET /api/v1/snapshots?granularity=period`
- **Então** o registro de "2025.2" tem `totalEmissionKg` igual à soma de todos os `daily_emission_kg` do período e campo `periodId` preenchido

#### AC-138 — Agregação semanal agrupa por semana ISO

- **Dado** que existem snapshots de segunda a sexta de uma mesma semana ISO para a minha instituição
- **Quando** faço `GET /api/v1/snapshots?granularity=weekly`
- **Então** existe exatamente um registro para essa semana com `totalEmissionKg` igual à soma dos 5 dias

#### AC-139 — Granularidade diária retorna um registro por snapshot_date

- **Dado** que existem 3 snapshots em datas distintas para a minha instituição
- **Quando** faço `GET /api/v1/snapshots?granularity=daily`
- **Então** a resposta contém exatamente 3 registros, um por data

#### AC-140 — variationPct calculado em relação ao registro imediatamente anterior

- **Dado** que existem dois registros mensais: Set 2025 com `totalEmissionKg = 1000` e Out 2025 com `totalEmissionKg = 1120` para a minha instituição
- **Quando** faço `GET /api/v1/snapshots?granularity=monthly`
- **Então** o registro de Out 2025 tem `variationPct = 12.0` (aumento de 12%)

#### AC-141 — Primeiro registro da série tem variationPct null

- **Dado** que existe apenas um snapshot mensal para a minha instituição (primeiro da série)
- **Quando** faço `GET /api/v1/snapshots?granularity=monthly`
- **Então** o único registro retornado tem `variationPct = null`

#### AC-142 — RLS: snapshots de outra instituição não são retornados

- **Dado** que a instituição B tem snapshots e a instituição A não tem nenhum
- **Quando** um usuário da instituição A faz `GET /api/v1/snapshots?granularity=monthly`
- **Então** a resposta é um array vazio (os dados da instituição B não aparecem)

#### AC-143 — Filtro startDate/endDate exclui registros fora do intervalo

- **Dado** que existem snapshots em março, abril e maio de 2025 para a minha instituição
- **Quando** faço `GET /api/v1/snapshots?granularity=monthly&startDate=2025-04-01&endDate=2025-04-30`
- **Então** apenas o registro de abril de 2025 é retornado

#### AC-144 — RESEARCHER consegue consultar snapshots

- **Dado** que um usuário com papel RESEARCHER está autenticado
- **Quando** faz `GET /api/v1/snapshots?granularity=monthly`
- **Então** recebe 200 OK (não 403)

---

### US-046 — Interface de acompanhamento com toggle de granularidade

Como gestor da instituição, quero visualizar o histórico de emissões em gráfico com toggle de granularidade, KPI cards e tabela histórica, para que eu acompanhe tendências de forma intuitiva.

#### AC-151 — Página exibe 3 KPI cards com valores calculados

- **Dado** que existem snapshots históricos para a instituição
- **Quando** acesso `/longitudinal`
- **Então** a página exibe 3 cards: "Emissão Atual" (total do mês mais recente), "Média Mensal" (média dos últimos 12 meses), "Menor Emissão" (mínimo mensal histórico)

#### AC-152 — Toggle de granularidade atualiza gráfico e tabela

- **Dado** que estou na página de acompanhamento com dados históricos disponíveis
- **Quando** seleciono "Semanal" no toggle de granularidade
- **Então** o gráfico de barras e a tabela histórica exibem dados agrupados por semana (os KPI cards não mudam)

#### AC-153 — Estado vazio exibe mensagem explicativa

- **Dado** que a instituição não tem nenhum snapshot ainda
- **Quando** acesso `/longitudinal`
- **Então** a página exibe uma mensagem explicando que a coleta automática gerará dados nos próximos dias letivos (sem tabela vazia nem gráfico quebrado)

#### AC-154 — Variação positiva é vermelha, negativa é verde

- **Dado** que a tabela histórica exibe registros com variação positiva e negativa
- **Quando** visualizo a coluna "VARIAÇÃO"
- **Então** valores positivos (aumento) aparecem com o token `--destructive` e negativos (redução) com o token `--success`

## Fora de escopo

- Captura automática diária por cron (US-045/AC-145–150 removidas — cron obsoleto, será reimplementado via ConsumptionResolver)
- Re-captura forçada de dia passado (V2)
- Comparação entre instituições
- Projeção de tendência futura
- KPI "Redução Possível" (movido para PRD 07 — Simulação de Cenários)
- Drill-down por laboratório no gráfico (V2)
- Notificação de falha do cron (V2)
- Snapshots para dias fora de período letivo
- Endpoint de deleção de snapshot

## Suposições

| ID | Suposição | Status | Resolução |
|---|---|---|---|
| ASM-031 | `LaboratorySchedule.occupiedSlots` armazena índices de slots de aula; horas do dia = `occupiedSlots.length × shift.classDurationMinutes / 60.0` | confirmada | lida no código de PeriodSummaryService |
| ASM-032 | Um dia pode ter schedules de múltiplos shifts para o mesmo lab; todas contribuem para o total diário | confirmada | lida no EmissionCalculationService — itera todos os shifts habilitados |
| ASM-033 | Granularidade semanal usa `DATE_TRUNC('week', ...)` do PostgreSQL (semana começa na segunda) | confirmada | lida no código de PeriodSummaryService |
| ASM-034 | Os KPI cards do frontend usam sempre granularity=monthly para os cálculos (não mudam com o toggle) | confirmada | definido no TDD e no design Pencil |

## Perguntas em aberto

| ID | Pergunta | Status | Resposta |
|---|---|---|---|
| Q-021 | Semana ISO começa na segunda ou no domingo no toggle "Semanal"? | respondida | Segunda-feira (ISO 8601, DATE_TRUNC('week')) |
