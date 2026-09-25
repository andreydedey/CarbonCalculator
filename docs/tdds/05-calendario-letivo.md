# TDD — Calendário Letivo e Ocupação dos Laboratórios

| Campo            | Valor                                          |
| ---------------- | ---------------------------------------------- |
| Tech Lead        | Andrey Dedey                                   |
| PRD de origem    | `docs/prds/05-calendario-letivo.md`            |
| ADRs relevantes  | —                                              |
| Status           | Pendente                                       |
| Criado em        | 2026-09-24                                     |
| Atualizado em    | 2026-09-25                                     |

---

## Contexto

O sistema já tem o cadastro completo do parque computacional (PRDs 02 e 03): instituições, laboratórios, modelos de computador, monitores e configurações com quantidades. Com isso, sabemos **quanto** cada estação consome (via TDP). O que falta é saber **por quanto tempo** — quantos dias letivos, em quais turnos e em quais laboratórios as máquinas operam.

O estudo de referência (FACOMP/UFPA) fez isso manualmente: considerou o turno vespertino, os horários de aula e os meses do período letivo, e agregou os resultados por laboratório, turno, dia da semana e mês. Foi assim que descobriu que segundas, quintas e sextas emitiam menos, e que março, com apenas nove dias letivos, parecia enganosamente melhor que abril.

O calendário letivo é a peça que transforma um valor pontual de consumo (watts) em energia consumida no período (kWh), que por sua vez será multiplicada pelo fator de emissão para gerar kg CO₂.

### Decisões resolvidas

- **A ocupação é constante ao longo do período letivo.** Assim como assumiu o estudo de referência, a grade semanal se repete uniformemente em todas as semanas do período. Não há diferenciação por semana específica.
- **Durante um horário ocupado, todas as máquinas do laboratório estão em uso.** Ocupação parcial (metade das máquinas ligada) fica fora do escopo — a suposição atual é conservadora (superestima levemente).
- **O consumo fora dos horários de aula não é considerado.** Máquinas ociosas fora do horário cadastrado não entram no cálculo. Isso pode subestimar, mas é consistente com o estudo de referência.
- **Feriados são cadastrados por data, não por tipo.** Não importa se é feriado municipal, estadual ou nacional — o que importa é que naquele dia o laboratório não operou.
- **O período letivo pertence à instituição, não ao laboratório.** É institucional: início, fim e feriados são comuns a todos os labs. A ocupação (grade horária) é por laboratório.
- **O período letivo não se sobrepõe a outro da mesma instituição.** Cada semestre ou ano letivo é um período independente. A validação impede sobreposição de datas.
- **Turnos são do período letivo, não do laboratório.** Existem 3 turnos fixos (Manhã, Tarde, Noite) configurados uma vez por período. Cada turno define: horário início/fim, quantidade de aulas por dia, duração da aula em minutos, duração do intervalo entre aulas em minutos, e dias ativos da semana. Turnos podem ser ativados ou desativados individualmente.
- **A ocupação é por slot de aula, não por bloco de horário.** Um turno com 5 aulas define 5 slots. Cada laboratório pode ocupar de 0 a N slots por turno por dia. Isso permite granularidade: um lab pode ter aula apenas nos 3 primeiros horários da manhã. O cálculo de horas usa: `slots_ocupados × duração_aula`.
- **O backend armazena quais slots individuais estão ocupados.** Embora para o cálculo de carbono a ordem não importe (3 × 50min = 150min), a UI permite marcar slots específicos (ex: 1ª e 3ª aula, mas não a 2ª). O backend guarda um array de números dos slots ocupados para preservar a seleção do usuário.

## Definição do Problema

O sistema consegue calcular o consumo instantâneo de um laboratório (soma dos TDPs das configurações), mas não tem como transformar isso em energia de um período, porque não sabe:

1. Quando o período letivo começa e termina
2. Quais dias são feriados ou recessos
3. Em quais dias da semana e horários cada laboratório opera

**O que acontece se não resolvermos:**
- O cálculo de emissões (PRD 06) não pode ser executado — ele depende de horas de uso por mês
- Não há como comparar meses com números diferentes de dias letivos
- Não há como diferenciar um laboratório que opera 4h/dia de outro que opera 12h/dia

## Escopo

### Dentro do escopo

- Entidade `AcademicPeriod` (período letivo) com datas de início/fim, feriados e vínculo à instituição
- Entidade `AcademicPeriodShift` (turno) com horários, duração de aula, intervalo entre aulas e dias ativos
- Entidade `LaboratorySchedule` (grade de ocupação) por laboratório × turno × dia, com contagem de slots ocupados
- Validação de sobreposição de períodos na mesma instituição
- Validação de que data final é posterior à inicial
- CRUD completo de períodos, turnos e grades via API REST
- Cálculo derivado: dias letivos por mês (descontando feriados e dias não-ativos do turno)
- Cálculo derivado: horas de uso por laboratório por mês (slots ocupados × duração da aula × dias letivos)
- Tela de gerenciamento de períodos letivos com cards de semestre
- Modal "Configurar Turnos" para definir a estrutura de cada turno
- Seção "Ocupação dos Laboratórios" com grid de slots por lab × dia × turno
- Tabela resumo de turnos e horários
- Tabela de feriados com calendário shadcn
- Cópia de calendário de um período anterior
- Seed data para desenvolvimento

### Fora do escopo

- Ocupação parcial (percentual de máquinas ligadas por slot)
- Consumo fora dos horários de aula (máquinas ociosas)
- Integração com calendário acadêmico oficial da instituição
- Reserva de laboratório ou alocação de turmas/disciplinas
- Diferenciação por semana dentro do mesmo período (a grade é uniforme)
- Turnos customizados além dos 3 fixos (Manhã, Tarde, Noite)

---

## Solução Técnica

### Visão geral da arquitetura

```
┌──────────────────────────────────────────────────────────┐
│  Frontend (React)                                        │
│                                                          │
│  pages/academic-periods       ──→  Lista de períodos     │
│  pages/academic-periods/:id   ──→  Detalhe do período    │
│                                                          │
│  Componentes: ShiftConfigModal, OccupationGrid,          │
│               HolidayEditor, ShiftSummaryTable            │
└──────────────────────────┬───────────────────────────────┘
                           │ HTTP (JSON)
┌──────────────────────────▼───────────────────────────────┐
│  Backend (Spring Boot)                                   │
│                                                          │
│  AcademicPeriodController → AcademicPeriodService        │
│  ShiftController → ShiftService                          │
│  LaboratoryScheduleController → LaboratoryScheduleServ.  │
│                                                          │
│  Cálculos derivados: dias letivos/mês, horas uso/mês    │
└──────────────────────────┬───────────────────────────────┘
                           │ JDBC + RLS
┌──────────────────────────▼───────────────────────────────┐
│  PostgreSQL                                              │
│  academic_period / academic_period_holiday /              │
│  academic_period_shift / laboratory_schedule              │
└──────────────────────────────────────────────────────────┘
```

### Modelo de dados

**Tabela `academic_period`**

| Coluna           | Tipo                | Restrições                                    |
| ---------------- | ------------------- | --------------------------------------------- |
| `id`             | `UUID`              | PK, gerado automaticamente                   |
| `institution_id` | `UUID`              | FK → institution(id), NOT NULL                |
| `name`           | `VARCHAR(100)`      | NOT NULL (ex: "2024.1", "2024/2º semestre")   |
| `start_date`     | `DATE`              | NOT NULL                                      |
| `end_date`       | `DATE`              | NOT NULL, CHECK (end_date > start_date)       |
| `created_at`     | `TIMESTAMP WITH TZ` | NOT NULL                                      |
| `updated_at`     | `TIMESTAMP WITH TZ` | NOT NULL                                      |

**Constraints:**
- CHECK: `end_date > start_date`
- EXCLUDE USING gist (institution_id WITH =, daterange(start_date, end_date, '[]') WITH &&) — impede sobreposição de períodos na mesma instituição
- RLS: `institution_id = current_setting('app.current_institution', true)::uuid`

**Índices:**
- `idx_academic_period_institution_id` — filtro por instituição (RLS)

**Tabela `academic_period_holiday`**

| Coluna               | Tipo                | Restrições                                    |
| -------------------- | ------------------- | --------------------------------------------- |
| `id`                 | `UUID`              | PK, gerado automaticamente                   |
| `academic_period_id` | `UUID`              | FK → academic_period(id) ON DELETE CASCADE    |
| `date`               | `DATE`              | NOT NULL                                      |
| `description`        | `VARCHAR(255)`      | NULL (ex: "Feriado de Natal", "Recesso")      |

**Constraints:**
- UNIQUE(academic_period_id, date) — não duplicar feriados
- O feriado deve estar dentro do intervalo [start_date, end_date] do período (validado no service)

**Tabela `academic_period_shift`**

Representa um turno de aula do período letivo. Cada período tem até 3 turnos (Manhã, Tarde, Noite). O turno define a estrutura temporal das aulas — horários, quantidade de slots e duração.

| Coluna                    | Tipo                | Restrições                                                   |
| ------------------------- | ------------------- | ------------------------------------------------------------ |
| `id`                      | `UUID`              | PK, gerado automaticamente                                  |
| `academic_period_id`      | `UUID`              | FK → academic_period(id) ON DELETE CASCADE                   |
| `shift_type`              | `VARCHAR(10)`       | NOT NULL, CHECK (MORNING, AFTERNOON, EVENING)                |
| `start_time`              | `TIME`              | NOT NULL (ex: 07:30)                                         |
| `end_time`                | `TIME`              | NOT NULL (ex: 11:50)                                         |
| `classes_per_day`         | `SMALLINT`          | NOT NULL, CHECK (> 0) (ex: 5)                                |
| `class_duration_minutes`  | `SMALLINT`          | NOT NULL, CHECK (> 0) (ex: 50)                                |
| `break_duration_minutes`  | `SMALLINT`          | NOT NULL, CHECK (>= 0) (ex: 10)                               |
| `active_days`             | `SMALLINT[]`        | NOT NULL (ex: {1,2,3,4,5} para Seg–Sex)                      |
| `enabled`                 | `BOOLEAN`           | NOT NULL, DEFAULT true                                       |
| `created_at`              | `TIMESTAMP WITH TZ` | NOT NULL                                                     |
| `updated_at`              | `TIMESTAMP WITH TZ` | NOT NULL                                                     |

**Constraints:**
- CHECK: `end_time > start_time`
- CHECK: `shift_type IN ('MORNING', 'AFTERNOON', 'EVENING')`
- CHECK: `classes_per_day > 0`
- CHECK: `class_duration_minutes > 0`
- CHECK: `break_duration_minutes >= 0`
- UNIQUE(academic_period_id, shift_type) — no máximo um turno de cada tipo por período

**Índices:**
- `idx_academic_period_shift_period` — (academic_period_id)

**Validação no service:** a duração total calculada `(classes_per_day × class_duration_minutes) + ((classes_per_day - 1) × break_duration_minutes)` deve caber dentro do intervalo `[start_time, end_time]`.

**Tabela `laboratory_schedule`**

Representa a ocupação de um laboratório em um turno num dia da semana. Cada linha é uma célula da grid (lab × turno × dia) com a quantidade de slots de aula ocupados.

| Coluna               | Tipo                | Restrições                                                   |
| -------------------- | ------------------- | ------------------------------------------------------------ |
| `id`                 | `UUID`              | PK, gerado automaticamente                                  |
| `shift_id`           | `UUID`              | FK → academic_period_shift(id) ON DELETE CASCADE             |
| `laboratory_id`      | `UUID`              | FK → laboratory(id) ON DELETE CASCADE                        |
| `day_of_week`        | `SMALLINT`          | NOT NULL, CHECK (1..7), 1=segunda, 7=domingo                 |
| `occupied_slots`     | `SMALLINT[]`        | NOT NULL — array dos números dos slots ocupados (1-indexed)   |
| `created_at`         | `TIMESTAMP WITH TZ` | NOT NULL                                                     |

**Constraints:**
- CHECK: `day_of_week BETWEEN 1 AND 7`
- UNIQUE(shift_id, laboratory_id, day_of_week) — uma célula por combinação
- Cada valor em `occupied_slots` deve estar entre 1 e `classes_per_day` do turno (validado no service)
- `day_of_week` deve estar contido em `active_days` do turno referenciado (validado no service)
- Linhas com `occupied_slots = '{}'` (array vazio) podem ser omitidas — ausência = 0 aulas

**Índices:**
- `idx_laboratory_schedule_shift_lab` — (shift_id, laboratory_id)

### Cálculos derivados

O backend expõe dois cálculos que o PRD 06 (emissões) consumirá:

**1. Dias letivos por mês (por turno)**

Para cada turno habilitado do período:
- Para cada dia ativo do turno (ex: Seg–Sex = {1,2,3,4,5}), contar quantas vezes aquele dia cai dentro de [start_date, end_date]
- Subtrair os feriados que caem naquele dia da semana
- Agrupar por mês

**2. Horas de uso por laboratório por mês**

Para um laboratório em um período:
- Para cada célula da grade (turno × dia da semana), calcular duração de uso: `len(occupied_slots) × class_duration_minutes` (em minutos, convertido para horas)
- Multiplicar pelos dias letivos correspondentes àquele dia da semana naquele mês
- Somar todos os turnos e dias

Exemplo: LABCOMP-01, turno Manhã (aula de 50min), segunda-feira, slots [1,2,3] ocupados, abril com 4 segundas letivas → 3 × 50min × 4 = 600min = 10h naquele mês só para esse turno nesse dia.

Esses cálculos são feitos no service e retornados pela API como dados derivados, não armazenados.

### API REST

**Períodos Letivos** (requer header `X-Institution-Id`)

| Método | Rota                                        | Descrição                                          | Acesso     |
| ------ | ------------------------------------------- | -------------------------------------------------- | ---------- |
| GET    | `/api/v1/academic-periods`                  | Listar períodos da instituição (paginado)          | RESEARCHER |
| POST   | `/api/v1/academic-periods`                  | Criar período                                      | MANAGER    |
| GET    | `/api/v1/academic-periods/{id}`             | Detalhe do período (com turnos, feriados)          | RESEARCHER |
| PUT    | `/api/v1/academic-periods/{id}`             | Atualizar período (datas, nome)                    | MANAGER    |
| DELETE | `/api/v1/academic-periods/{id}`             | Excluir período (cascade em tudo)                  | MANAGER    |
| POST   | `/api/v1/academic-periods/{id}/copy`        | Copiar calendário de outro período                 | MANAGER    |

**Turnos** (sub-recurso do período)

| Método | Rota                                                     | Descrição                                        | Acesso     |
| ------ | -------------------------------------------------------- | ------------------------------------------------ | ---------- |
| PUT    | `/api/v1/academic-periods/{id}/shifts`                   | Substituir configuração de turnos (batch)        | MANAGER    |

**Feriados** (sub-recurso do período)

| Método | Rota                                                     | Descrição                                        | Acesso     |
| ------ | -------------------------------------------------------- | ------------------------------------------------ | ---------- |
| PUT    | `/api/v1/academic-periods/{id}/holidays`                 | Substituir lista de feriados (batch)             | MANAGER    |

**Grade de Ocupação** (por laboratório no período)

| Método | Rota                                                                        | Descrição                                        | Acesso     |
| ------ | --------------------------------------------------------------------------- | ------------------------------------------------ | ---------- |
| GET    | `/api/v1/academic-periods/{periodId}/laboratories/{labId}/schedule`         | Obter grade do laboratório (todos os turnos)     | RESEARCHER |
| PUT    | `/api/v1/academic-periods/{periodId}/laboratories/{labId}/schedule`         | Substituir grade do laboratório (batch)          | MANAGER    |

**Dados derivados**

| Método | Rota                                                                        | Descrição                                        | Acesso     |
| ------ | --------------------------------------------------------------------------- | ------------------------------------------------ | ---------- |
| GET    | `/api/v1/academic-periods/{id}/summary`                                    | Dias letivos por mês + horas/lab/mês             | RESEARCHER |

### Contratos da API

```json
// POST /api/v1/academic-periods
// Request
{
  "name": "2025.1",
  "startDate": "2025-03-10",
  "endDate": "2025-07-18"
}

// Response 201
{
  "id": "550e8400-...",
  "name": "2025.1",
  "startDate": "2025-03-10",
  "endDate": "2025-07-18",
  "holidayCount": 0,
  "shifts": [],
  "createdAt": "2026-09-25T10:00:00Z"
}
```

```json
// PUT /api/v1/academic-periods/{id}/shifts
// Request — substitui toda a configuração de turnos
{
  "shifts": [
    {
      "shiftType": "MORNING",
      "startTime": "07:30",
      "endTime": "11:50",
      "classesPerDay": 5,
      "classDurationMinutes": 50,
      "breakDurationMinutes": 10,
      "activeDays": [1, 2, 3, 4, 5],
      "enabled": true
    },
    {
      "shiftType": "AFTERNOON",
      "startTime": "13:30",
      "endTime": "17:50",
      "classesPerDay": 5,
      "classDurationMinutes": 50,
      "breakDurationMinutes": 10,
      "activeDays": [1, 2, 3, 4, 5],
      "enabled": true
    },
    {
      "shiftType": "EVENING",
      "startTime": "18:50",
      "endTime": "22:20",
      "classesPerDay": 4,
      "classDurationMinutes": 50,
      "breakDurationMinutes": 10,
      "activeDays": [1, 2, 3, 4, 5],
      "enabled": false
    }
  ]
}

// Response 200
{
  "shifts": [
    {
      "id": "aaa-...",
      "shiftType": "MORNING",
      "startTime": "07:30",
      "endTime": "11:50",
      "classesPerDay": 5,
      "classDurationMinutes": 50,
      "breakDurationMinutes": 10,
      "activeDays": [1, 2, 3, 4, 5],
      "enabled": true
    },
    ...
  ]
}
```

```json
// PUT /api/v1/academic-periods/{id}/holidays
// Request — substitui toda a lista
{
  "holidays": [
    { "date": "2025-03-29", "description": "Sexta-feira Santa" },
    { "date": "2025-04-21", "description": "Tiradentes" },
    { "date": "2025-05-01", "description": "Dia do Trabalho" },
    { "date": "2025-06-19", "description": "Corpus Christi" },
    { "date": "2025-08-15", "description": "Adesão do Pará" }
  ]
}

// Response 200
{
  "holidays": [
    { "date": "2025-03-29", "description": "Sexta-feira Santa" },
    ...
  ]
}
```

```json
// PUT /api/v1/academic-periods/{periodId}/laboratories/{labId}/schedule
// Request — substitui toda a grade deste lab (lista de células ocupadas)
{
  "entries": [
    { "shiftId": "aaa-...", "dayOfWeek": 1, "occupiedSlots": [1, 2, 3, 4, 5] },
    { "shiftId": "aaa-...", "dayOfWeek": 2, "occupiedSlots": [1, 2, 3] },
    { "shiftId": "aaa-...", "dayOfWeek": 3, "occupiedSlots": [1, 2, 3, 4, 5] },
    { "shiftId": "bbb-...", "dayOfWeek": 1, "occupiedSlots": [1, 3, 4, 5] },
    { "shiftId": "bbb-...", "dayOfWeek": 4, "occupiedSlots": [2, 4] }
  ]
}

// Response 200
{
  "laboratoryId": "...",
  "laboratoryName": "LABCOMP-01",
  "entries": [
    { "shiftId": "aaa-...", "shiftType": "MORNING", "dayOfWeek": 1, "occupiedSlots": [1, 2, 3, 4, 5] },
    { "shiftId": "aaa-...", "shiftType": "MORNING", "dayOfWeek": 2, "occupiedSlots": [1, 2, 3] },
    ...
  ]
}
```

```json
// GET /api/v1/academic-periods/{id}/summary
// Response 200
{
  "period": {
    "id": "...",
    "name": "2025.1",
    "startDate": "2025-03-10",
    "endDate": "2025-07-18"
  },
  "schoolDaysPerMonth": [
    { "month": "2025-03", "schoolDays": 16 },
    { "month": "2025-04", "schoolDays": 20 },
    { "month": "2025-05", "schoolDays": 21 },
    { "month": "2025-06", "schoolDays": 20 },
    { "month": "2025-07", "schoolDays": 14 }
  ],
  "laboratorySummaries": [
    {
      "laboratoryId": "...",
      "laboratoryName": "LABCOMP-01",
      "hoursPerMonth": [
        { "month": "2025-03", "hours": 53.3 },
        { "month": "2025-04", "hours": 66.7 },
        { "month": "2025-05", "hours": 70.0 },
        { "month": "2025-06", "hours": 66.7 },
        { "month": "2025-07", "hours": 46.7 }
      ],
      "totalHours": 303.4
    }
  ]
}
```

### Frontend

**Páginas (conforme design no pencil):**

| Página                   | Rota                                             | Frame no design                     | Descrição                                                         |
| ------------------------ | ------------------------------------------------ | ----------------------------------- | ----------------------------------------------------------------- |
| Calendário Letivo        | `/academic-periods`                              | 5 – Calendário Letivo               | Cards de semestre, tabela de turnos, grid resumo de ocupação, tabela de feriados |
| Ocupação dos Laboratórios| `/academic-periods/:id/occupation`               | 5c – Ocupação dos Laboratórios      | Edição da grade de ocupação por lab com slots individuais          |

**Modal:**

| Modal                    | Frame no design                     | Descrição                                                         |
| ------------------------ | ----------------------------------- | ----------------------------------------------------------------- |
| Configurar Turnos        | 5b – Configurar Turnos              | Define estrutura dos turnos: horários, aulas/dia, duração, intervalo, dias ativos |

**Componentes principais (derivados do design):**

- `AcademicPeriodList` — não é uma lista separada; a página principal mostra cards de semestre lado a lado (ex: "2025.1 — Primeiro Semestre" com badge "Em andamento", datas, total de dias letivos, botões "Editar" e "Configurar Turnos")
- `AcademicPeriodForm` — dialog para criar/editar período (nome, datas)
- `ShiftConfigModal` — modal "Configurar Turnos" (frame 5b): 3 seções (Manhã/Tarde/Noite) com switch on/off, campos Horário Início, Aulas/Dia, Duração (min), Intervalo (min), select de Dias Ativos, preview visual dos slots com horários calculados. O Horário Fim é calculado: `start + (aulas × duração) + ((aulas-1) × intervalo)`
- `ShiftSummaryTable` — tabela "Turnos e Horários de Aula" com colunas: Turno, Horário Início, Fim (calculado), Aulas/Dia, Duração Aula, Intervalo, Dias Ativos
- `OccupationSummaryGrid` — grid resumo na página principal: linhas = labs, colunas = dias (Seg–Sex) com mini-strips agrupados por turno (M/T/N), colunas AULAS e HORAS/SEM no final, legenda e totais no footer
- `OccupationEditor` — página dedicada (frame 5c): tabs por laboratório (pill-style), botão "Copiar ocupação de outro laboratório", 3 stats cards (Aulas/Semana, Horas/Semana, Taxa de Ocupação com barra de progresso), grade semanal completa com linhas individuais por slot
- `OccupationGrid` — a grade semanal dentro do OccupationEditor: colunas AULA | Seg | Ter | Qua | Qui | Sex | TOTAL; seções por turno (header com ícone, horário, "18 de 25 aulas"); uma linha por slot de aula com horário exato (ex: "1ª aula · 07:30–08:20") e checkbox por dia; linhas de intervalo entre slots (ex: "intervalo 10 min · 08:20–08:30"); footer com total por dia em horas e total geral
- `HolidayEditor` — tabela "Feriados e Recessos" com colunas Data, Feriado/Recesso, Tipo; botão "Adicionar" com date picker (calendário shadcn); badges por tipo de feriado

**Navegação:**
- Novo item na sidebar: "Calendário Letivo" (ícone `calendar`), visível para todos, na seção "Cadastro"
- Ações de escrita (criar, editar, excluir, configurar turnos, salvar ocupação) restritas a MANAGER no frontend
- Breadcrumb: "Cadastro › Calendário Letivo" na página principal; "Cadastro › Calendário Letivo › Ocupação" na página de ocupação

### Dependências

**Backend:**
- Nenhuma nova. Usa Spring Data JPA, Flyway, PostgreSQL (existentes).
- PostgreSQL `btree_gist` extension para EXCLUDE constraint de sobreposição de datas.

**Frontend:**
- Nenhuma nova. Usa shadcn/ui (calendário, date picker, tabela), React Hook Form, Zod (existentes).

---

## Riscos

| Risco | Impacto | Probabilidade | Mitigação |
|-------|---------|---------------|-----------|
| Extensão `btree_gist` não disponível no PostgreSQL do deploy | Médio | Baixa | Validar sobreposição no service como fallback; `btree_gist` é padrão no PostgreSQL |
| Grade de ocupação com muitos slots gera muitas linhas visuais | Médio | Baixa | O design agrupa por turno com headers colapsáveis; turnos desativados não aparecem |
| Cópia de período anterior com dados inválidos (ex: feriado fora do novo range) | Baixo | Média | Filtrar feriados que caem fora do novo intervalo durante a cópia |
| Performance do cálculo de dias letivos com muitos feriados e períodos longos | Baixo | Baixa | Cálculo em memória no Java, sem queries extras — complexidade O(dias_do_período) |
| Alteração de turnos invalida grades de ocupação existentes | Médio | Média | Ao reduzir `classes_per_day`, remover slots que excedem o novo limite; ao desativar turno, manter dados mas não contabilizar |

---

## Plano de Implementação

| Fase | Tarefa | Descrição | Status |
|------|--------|-----------|--------|
| **1 — Banco** | Extension btree_gist | Ativar extensão para EXCLUDE constraint | Pendente |
| **1 — Banco** | Migration — academic_period | Criar tabela com RLS, CHECK e EXCLUDE | Pendente |
| **1 — Banco** | Migration — academic_period_holiday | Criar tabela de feriados com FK cascade | Pendente |
| **1 — Banco** | Migration — academic_period_shift | Criar tabela de turnos com CHECK e UNIQUE | Pendente |
| **1 — Banco** | Migration — laboratory_schedule | Criar tabela de grade com FK e UNIQUE | Pendente |
| **1 — Banco** | Entities JPA | AcademicPeriod, AcademicPeriodHoliday, AcademicPeriodShift, LaboratorySchedule | Pendente |
| **2 — Backend** | AcademicPeriodService | CRUD de períodos com validação de sobreposição | Pendente |
| **2 — Backend** | AcademicPeriodController | Endpoints REST de períodos | Pendente |
| **2 — Backend** | ShiftService | CRUD de turnos com validação de duração | Pendente |
| **2 — Backend** | HolidayService | PUT batch de feriados com validação de intervalo | Pendente |
| **2 — Backend** | LaboratoryScheduleService | PUT batch de grade com validação de slots | Pendente |
| **2 — Backend** | LaboratoryScheduleController | Endpoints REST de grade | Pendente |
| **2 — Backend** | PeriodSummaryService | Cálculo de dias letivos/mês e horas/lab/mês | Pendente |
| **2 — Backend** | Copy period | Duplicar período com turnos, feriados e grades | Pendente |
| **3 — Frontend** | API client | Funções para academic-periods, shifts, holidays e schedules | Pendente |
| **3 — Frontend** | Sidebar + rotas | Adicionar "Calendário Letivo" na sidebar e rotas | Pendente |
| **3 — Frontend** | Página Calendário Letivo | Cards de semestre, ShiftSummaryTable, OccupationSummaryGrid, HolidayEditor | Pendente |
| **3 — Frontend** | AcademicPeriodForm | Dialog de criação/edição de período (nome, datas) | Pendente |
| **3 — Frontend** | ShiftConfigModal | Modal de configuração de turnos (frame 5b) | Pendente |
| **3 — Frontend** | HolidayEditor | Tabela de feriados com calendário shadcn e badges de tipo | Pendente |
| **3 — Frontend** | OccupationEditor | Página de ocupação (frame 5c): tabs por lab, stats, grade semanal com slots | Pendente |
| **4 — Seed** | afterMigrate.sql | Adicionar período 2025.1 com turnos, feriados e grades ao seed | Pendente |

---

## Estratégia de Testes

| Tipo | Escopo | Abordagem |
|------|--------|-----------|
| **Unitário (backend)** | Cálculo de dias letivos, validação de turnos | Testes isolados do PeriodSummaryService e ShiftService |
| **Integração (backend)** | CRUD + RLS + sobreposição + turnos + grades | Requisições HTTP com PostgreSQL real |

**Cenários críticos a testar:**

**Períodos:**
- Criar período com datas válidas → 201
- Criar período com data final anterior à inicial → 400
- Criar período sobrepondo outro da mesma instituição → 409
- Criar período sem sobreposição → 201
- Excluir período → cascade remove feriados, turnos e grades

**Turnos:**
- Configurar turno com dados válidos → 200
- Turno com `classes_per_day = 0` → 400
- Turno com `class_duration_minutes = 0` → 400
- Turno com duração total que excede o intervalo [start_time, end_time] → 400
- Desativar turno → ocupação do turno não contabilizada no cálculo
- Dois turnos do mesmo tipo no mesmo período → 400 (UNIQUE)

**Feriados:**
- Adicionar feriado dentro do intervalo → OK
- Adicionar feriado fora do intervalo → 400
- Feriado duplicado na mesma data → ignorado (upsert)

**Grade de ocupação:**
- Salvar grade com slots válidos (ex: [1, 2, 3] para turno com 5 aulas) → 200
- Slot fora do range (ex: slot 6 para turno com 5 aulas) → 400
- Dia da semana fora dos active_days do turno → 400
- Dia da semana fora de 1-7 → 400
- Grade com array vazio de slots → aceito (0 aulas nesse dia)

**Cálculos derivados:**
- Período com 0 feriados: dias letivos = dias ativos do turno no intervalo
- Período com feriado em dia ativo: desconta 1 dia naquele mês
- Período com feriado em dia não-ativo: não altera contagem
- Lab com 3 slots de 50min seg-sex: horas/mês = 3 × 50min × dias_letivos_do_mês ÷ 60
- Lab sem grade definida: 0 horas (pendência indicada)
- Lab com slots em turnos diferentes: horas somam corretamente

**Isolamento (RLS):**
- Instituição A não enxerga períodos da instituição B
- Grade de lab da instituição A não é visível para instituição B
