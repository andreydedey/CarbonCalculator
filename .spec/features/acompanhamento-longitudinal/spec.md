# Spec: Acompanhamento longitudinal

> feature: acompanhamento-longitudinal
> status: rascunho

## Contexto

Gestor de instituição precisa acompanhar como as emissões de CO₂ evoluem ao longo do tempo, com a granularidade que for mais útil (diária, semanal, mensal ou por período letivo). Um cron job diário captura automaticamente os dados de cada dia dentro de um período letivo ativo; a interface agrega esses snapshots conforme a granularidade selecionada.

## Histórias

### US-033 — Consulta histórica de emissões por granularidade

Como gestor da instituição, quero consultar o histórico de emissões com controle de granularidade (diária, semanal, mensal, por período), para que eu identifique tendências e anomalias com o nível de detalhe adequado.

#### AC-103 — Agregação mensal soma corretamente dias do mesmo mês

- **Dado** que existem 5 snapshots diários em outubro de 2025, com `daily_emission_kg` de 100, 120, 90, 110 e 130 para a minha instituição
- **Quando** faço `GET /api/v1/snapshots?granularity=monthly`
- **Então** o registro de "Out 2025" tem `totalEmissionKg = 550` e `schoolDays = 5`

#### AC-104 — Agregação por período letivo soma todos os dias do período

- **Dado** que existem snapshots diários distribuídos ao longo do período "2025.2" para a minha instituição
- **Quando** faço `GET /api/v1/snapshots?granularity=period`
- **Então** o registro de "2025.2" tem `totalEmissionKg` igual à soma de todos os `daily_emission_kg` do período e campo `periodId` preenchido

#### AC-105 — Agregação semanal agrupa por semana ISO

- **Dado** que existem snapshots de segunda a sexta de uma mesma semana ISO para a minha instituição
- **Quando** faço `GET /api/v1/snapshots?granularity=weekly`
- **Então** existe exatamente um registro para essa semana com `totalEmissionKg` igual à soma dos 5 dias

#### AC-106 — Granularidade diária retorna um registro por snapshot_date

- **Dado** que existem 3 snapshots em datas distintas para a minha instituição
- **Quando** faço `GET /api/v1/snapshots?granularity=daily`
- **Então** a resposta contém exatamente 3 registros, um por data

#### AC-107 — variationPct calculado em relação ao registro imediatamente anterior

- **Dado** que existem dois registros mensais: Set 2025 com `totalEmissionKg = 1000` e Out 2025 com `totalEmissionKg = 1120` para a minha instituição
- **Quando** faço `GET /api/v1/snapshots?granularity=monthly`
- **Então** o registro de Out 2025 tem `variationPct = 12.0` (aumento de 12%)

#### AC-108 — Primeiro registro da série tem variationPct null

- **Dado** que existe apenas um snapshot mensal para a minha instituição (primeiro da série)
- **Quando** faço `GET /api/v1/snapshots?granularity=monthly`
- **Então** o único registro retornado tem `variationPct = null`

#### AC-109 — RLS: snapshots de outra instituição não são retornados

- **Dado** que a instituição B tem snapshots e a instituição A não tem nenhum
- **Quando** um usuário da instituição A faz `GET /api/v1/snapshots?granularity=monthly`
- **Então** a resposta é um array vazio (os dados da instituição B não aparecem)

#### AC-110 — Filtro startDate/endDate exclui registros fora do intervalo

- **Dado** que existem snapshots em março, abril e maio de 2025 para a minha instituição
- **Quando** faço `GET /api/v1/snapshots?granularity=monthly&startDate=2025-04-01&endDate=2025-04-30`
- **Então** apenas o registro de abril de 2025 é retornado

#### AC-111 — RESEARCHER consegue consultar snapshots

- **Dado** que um usuário com papel RESEARCHER está autenticado
- **Quando** faz `GET /api/v1/snapshots?granularity=monthly`
- **Então** recebe 200 OK (não 403)

---

### US-034 — Captura automática diária de emissões por cron

Como sistema, quero capturar automaticamente as emissões de cada dia dentro de períodos letivos ativos, para que o histórico seja construído sem intervenção manual.

#### AC-112 — Cron cria snapshot para dia dentro de período ativo com fator disponível

- **Dado** que ontem estava dentro de um período letivo ativo da instituição e existe um fator SIN para o mês de ontem
- **Quando** o cron job é executado
- **Então** um `EmissionSnapshot` é criado com `snapshot_date = ontem`, `daily_emission_kg > 0` (se houver labs com schedule), e `emission_factor_value` igual ao fator SIN do mês

#### AC-113 — Cron é idempotente — execução duplicada não cria novo snapshot

- **Dado** que já existe um snapshot para a data de ontem da instituição
- **Quando** o cron job é executado novamente para o mesmo dia (retry)
- **Então** nenhum novo snapshot é criado e o existente permanece inalterado

#### AC-114 — Cron não cria snapshot para dia fora de período letivo

- **Dado** que ontem não estava dentro de nenhum período letivo ativo da instituição
- **Quando** o cron job é executado
- **Então** nenhum snapshot é criado para essa instituição

#### AC-115 — Cron não cria snapshot quando fator SIN está ausente

- **Dado** que ontem estava dentro de um período letivo ativo mas não existe fator SIN cadastrado para o mês
- **Quando** o cron job é executado
- **Então** nenhum snapshot é criado e um aviso é logado (snapshot omitido, não erro fatal)

#### AC-116 — Dia feriado gera snapshot com emissão zero

- **Dado** que ontem estava dentro de um período letivo mas é um feriado cadastrado (`AcademicPeriodHoliday`)
- **Quando** o cron job é executado
- **Então** um snapshot é criado com `daily_emission_kg = 0`, `daily_energy_kwh = 0` e `is_school_day = false`

#### AC-117 — Lab sem schedule para o dayOfWeek do dia não contribui para emissão

- **Dado** que ontem foi uma terça-feira, o laboratório A tem `LaboratorySchedule` apenas para segunda e o laboratório B tem `LaboratorySchedule` para terça
- **Quando** o cron job é executado
- **Então** apenas o laboratório B contribui para `daily_energy_kwh` do snapshot

---

### US-035 — Interface de acompanhamento com toggle de granularidade

Como gestor da instituição, quero visualizar o histórico de emissões em gráfico com toggle de granularidade, KPI cards e tabela histórica, para que eu acompanhe tendências de forma intuitiva.

#### AC-118 — Página exibe 4 KPI cards com valores calculados

- **Dado** que existem snapshots históricos para a instituição
- **Quando** acesso `/longitudinal`
- **Então** a página exibe 4 cards: "Emissão Atual" (total do mês mais recente), "Média Mensal" (média dos últimos 12 meses), "Menor Emissão" (mínimo mensal histórico), "Redução Possível" (placeholder "—")

#### AC-119 — Toggle de granularidade atualiza gráfico e tabela

- **Dado** que estou na página de acompanhamento com dados históricos disponíveis
- **Quando** seleciono "Semanal" no toggle de granularidade
- **Então** o gráfico de barras e a tabela histórica exibem dados agrupados por semana (os KPI cards não mudam)

#### AC-120 — Estado vazio exibe mensagem explicativa

- **Dado** que a instituição não tem nenhum snapshot ainda
- **Quando** acesso `/longitudinal`
- **Então** a página exibe uma mensagem explicando que a coleta automática gerará dados nos próximos dias letivos (sem tabela vazia nem gráfico quebrado)

#### AC-121 — Variação positiva é vermelha, negativa é verde

- **Dado** que a tabela histórica exibe registros com variação positiva e negativa
- **Quando** visualizo a coluna "VARIAÇÃO"
- **Então** valores positivos (aumento) aparecem em vermelho (#DC2626) e negativos (redução) em verde (#16A34A)

## Fora de escopo

- Re-captura forçada de dia passado (V2)
- Comparação entre instituições
- Projeção de tendência futura
- KPI "Redução Possível" funcional (depende de PRD 07)
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
