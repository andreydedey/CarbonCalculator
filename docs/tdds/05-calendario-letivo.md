# TDD — Calendário Letivo e Ocupação dos Laboratórios

| Campo            | Valor                                          |
| ---------------- | ---------------------------------------------- |
| Tech Lead        | Andrey Dedey                                   |
| PRD de origem    | `docs/prds/05-calendario-letivo.md`            |
| ADRs relevantes  | —                                              |
| Status           | Pendente                                       |
| Criado em        | 2026-09-24                                     |
| Atualizado em    | 2026-09-24                                     |

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
- Entidade `LaboratorySchedule` (grade de ocupação semanal) por laboratório e período
- Validação de sobreposição de períodos na mesma instituição
- Validação de que data final é posterior à inicial
- CRUD completo de períodos e grades via API REST
- Cálculo derivado: dias letivos por mês (descontando feriados e fins de semana)
- Cálculo derivado: horas de uso por laboratório por mês
- Tela de gerenciamento de períodos letivos
- Tela de grade de ocupação por laboratório
- Cópia de calendário de um período anterior
- Seed data para desenvolvimento

### Fora do escopo

- Ocupação parcial (percentual de máquinas ligadas)
- Consumo fora dos horários de aula (máquinas ociosas)
- Integração com calendário acadêmico oficial da instituição
- Reserva de laboratório ou alocação de turmas/disciplinas
- Diferenciação por semana dentro do mesmo período (a grade é uniforme)

---

## Solução Técnica

### Visão geral da arquitetura

```
┌─────────────────────────────────────────────────────────┐
│  Frontend (React)                                       │
│                                                         │
│  pages/academic-periods  ──→  CRUD de períodos          │
│  pages/laboratory-schedule ──→  Grade semanal por lab   │
│                                                         │
│  Componentes: calendário de feriados, grade horária     │
└──────────────────────────┬──────────────────────────────┘
                           │ HTTP (JSON)
┌──────────────────────────▼──────────────────────────────┐
│  Backend (Spring Boot)                                  │
│                                                         │
│  AcademicPeriodController → AcademicPeriodService       │
│  LaboratoryScheduleController → LaboratoryScheduleServ │
│                                                         │
│  Cálculos derivados: dias letivos/mês, horas uso/mês   │
└──────────────────────────┬──────────────────────────────┘
                           │ JDBC + RLS
┌──────────────────────────▼──────────────────────────────┐
│  PostgreSQL                                             │
│  academic_period / academic_period_holiday /             │
│  laboratory_schedule                                    │
└─────────────────────────────────────────────────────────┘
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

**Tabela `laboratory_schedule`**

Representa a grade de ocupação semanal de um laboratório em um período letivo. Cada linha é um bloco de horário em um dia da semana.

| Coluna               | Tipo                | Restrições                                    |
| -------------------- | ------------------- | --------------------------------------------- |
| `id`                 | `UUID`              | PK, gerado automaticamente                   |
| `academic_period_id` | `UUID`              | FK → academic_period(id) ON DELETE CASCADE    |
| `laboratory_id`      | `UUID`              | FK → laboratory(id) ON DELETE CASCADE         |
| `day_of_week`        | `SMALLINT`          | NOT NULL, CHECK (1..7), 1=segunda, 7=domingo  |
| `start_time`         | `TIME`              | NOT NULL                                      |
| `end_time`           | `TIME`              | NOT NULL, CHECK (end_time > start_time)       |
| `created_at`         | `TIMESTAMP WITH TZ` | NOT NULL                                      |

**Constraints:**
- CHECK: `end_time > start_time`
- CHECK: `day_of_week BETWEEN 1 AND 7`
- Não há blocos que se sobreponham para o mesmo lab+período+dia (validado no service ou via EXCLUDE)

**Índices:**
- `idx_laboratory_schedule_period_lab` — (academic_period_id, laboratory_id)

### Cálculos derivados

O backend expõe dois cálculos que o PRD 06 (emissões) consumirá:

**1. Dias letivos por mês**

Para um período e um dia da semana:
- Contar quantas vezes aquele dia cai dentro de [start_date, end_date]
- Subtrair os feriados que caem naquele dia da semana
- Agrupar por mês

**2. Horas de uso por laboratório por mês**

Para um laboratório em um período:
- Para cada bloco da grade semanal, calcular duração em horas (end_time - start_time)
- Multiplicar pelos dias letivos correspondentes àquele dia da semana no mês
- Somar todos os blocos

Esses cálculos são feitos no service e retornados pela API como dados derivados, não armazenados.

### API REST

**Períodos Letivos** (requer header `X-Institution-Id`)

| Método | Rota                                        | Descrição                                          | Acesso     |
| ------ | ------------------------------------------- | -------------------------------------------------- | ---------- |
| GET    | `/api/v1/academic-periods`                  | Listar períodos da instituição (paginado)          | RESEARCHER |
| POST   | `/api/v1/academic-periods`                  | Criar período                                      | MANAGER    |
| GET    | `/api/v1/academic-periods/{id}`             | Detalhe do período (com feriados e dias por mês)   | RESEARCHER |
| PUT    | `/api/v1/academic-periods/{id}`             | Atualizar período (datas, nome)                    | MANAGER    |
| DELETE | `/api/v1/academic-periods/{id}`             | Excluir período (cascade remove feriados e grades) | MANAGER    |
| POST   | `/api/v1/academic-periods/{id}/copy`        | Copiar calendário de outro período                 | MANAGER    |

**Feriados** (sub-recurso do período)

| Método | Rota                                                     | Descrição              | Acesso     |
| ------ | -------------------------------------------------------- | ---------------------- | ---------- |
| PUT    | `/api/v1/academic-periods/{id}/holidays`                 | Substituir lista de feriados (batch) | MANAGER |

**Grade de Ocupação** (por laboratório no período)

| Método | Rota                                                                        | Descrição                            | Acesso     |
| ------ | --------------------------------------------------------------------------- | ------------------------------------ | ---------- |
| GET    | `/api/v1/academic-periods/{periodId}/laboratories/{labId}/schedule`         | Obter grade do laboratório           | RESEARCHER |
| PUT    | `/api/v1/academic-periods/{periodId}/laboratories/{labId}/schedule`         | Substituir grade do laboratório (batch) | MANAGER |

**Dados derivados**

| Método | Rota                                                                        | Descrição                                  | Acesso     |
| ------ | --------------------------------------------------------------------------- | ------------------------------------------ | ---------- |
| GET    | `/api/v1/academic-periods/{id}/summary`                                    | Dias letivos por mês + horas/lab/mês       | RESEARCHER |

### Contratos da API

```json
// POST /api/v1/academic-periods
// Request
{
  "name": "2024.1",
  "startDate": "2024-03-04",
  "endDate": "2024-07-12"
}

// Response 201
{
  "id": "550e8400-...",
  "name": "2024.1",
  "startDate": "2024-03-04",
  "endDate": "2024-07-12",
  "holidayCount": 0,
  "createdAt": "2026-09-24T10:00:00Z"
}
```

```json
// PUT /api/v1/academic-periods/{id}/holidays
// Request — substitui toda a lista
{
  "holidays": [
    { "date": "2024-03-29", "description": "Sexta-feira Santa" },
    { "date": "2024-04-21", "description": "Tiradentes" },
    { "date": "2024-05-01", "description": "Dia do Trabalho" },
    { "date": "2024-05-30", "description": "Corpus Christi" },
    { "date": "2024-06-24", "description": "São João" }
  ]
}

// Response 200
{
  "holidays": [
    { "date": "2024-03-29", "description": "Sexta-feira Santa" },
    { "date": "2024-04-21", "description": "Tiradentes" },
    { "date": "2024-05-01", "description": "Dia do Trabalho" },
    { "date": "2024-05-30", "description": "Corpus Christi" },
    { "date": "2024-06-24", "description": "São João" }
  ]
}
```

```json
// PUT /api/v1/academic-periods/{periodId}/laboratories/{labId}/schedule
// Request — substitui toda a grade
{
  "blocks": [
    { "dayOfWeek": 1, "startTime": "14:00", "endTime": "18:00" },
    { "dayOfWeek": 2, "startTime": "14:00", "endTime": "18:00" },
    { "dayOfWeek": 3, "startTime": "14:00", "endTime": "18:00" },
    { "dayOfWeek": 4, "startTime": "14:00", "endTime": "18:00" },
    { "dayOfWeek": 5, "startTime": "14:00", "endTime": "18:00" }
  ]
}

// Response 200
{
  "laboratoryId": "...",
  "laboratoryName": "LABCOMP-01",
  "blocks": [
    { "dayOfWeek": 1, "startTime": "14:00", "endTime": "18:00" },
    { "dayOfWeek": 2, "startTime": "14:00", "endTime": "18:00" },
    { "dayOfWeek": 3, "startTime": "14:00", "endTime": "18:00" },
    { "dayOfWeek": 4, "startTime": "14:00", "endTime": "18:00" },
    { "dayOfWeek": 5, "startTime": "14:00", "endTime": "18:00" }
  ]
}
```

```json
// GET /api/v1/academic-periods/{id}/summary
// Response 200
{
  "period": {
    "id": "...",
    "name": "2024.1",
    "startDate": "2024-03-04",
    "endDate": "2024-07-12"
  },
  "schoolDaysPerMonth": [
    { "month": "2024-03", "schoolDays": 9 },
    { "month": "2024-04", "schoolDays": 20 },
    { "month": "2024-05", "schoolDays": 21 },
    { "month": "2024-06", "schoolDays": 19 },
    { "month": "2024-07", "schoolDays": 9 }
  ],
  "laboratorySummaries": [
    {
      "laboratoryId": "...",
      "laboratoryName": "LABCOMP-01",
      "hoursPerMonth": [
        { "month": "2024-03", "hours": 36.0 },
        { "month": "2024-04", "hours": 80.0 },
        { "month": "2024-05", "hours": 84.0 },
        { "month": "2024-06", "hours": 76.0 },
        { "month": "2024-07", "hours": 36.0 }
      ],
      "totalHours": 312.0
    }
  ]
}
```

### Frontend

**Páginas:**

| Página                  | Rota                                     | Descrição                                                         |
| ----------------------- | ---------------------------------------- | ----------------------------------------------------------------- |
| Períodos Letivos        | `/academic-periods`                      | Lista de períodos com nome, datas, contagem de feriados           |
| Detalhe do Período      | `/academic-periods/:id`                  | Feriados + grades de ocupação por lab + resumo de dias/horas      |

**Componentes principais:**

- `AcademicPeriodList` — tabela/cards de períodos com busca e paginação
- `AcademicPeriodForm` — dialog para criar/editar período (nome, datas)
- `HolidayEditor` — lista editável de feriados com date picker
- `ScheduleGrid` — grade visual semanal (dias × horas) por laboratório
- `PeriodSummary` — tabela de dias letivos e horas por mês

**Navegação:**
- Novo item na sidebar: "Calendário" (ícone Calendar), visível para todos
- Ações de escrita (criar, editar, excluir) restritas a MANAGER no frontend

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
| Grades de ocupação complexas (múltiplos blocos por dia) tornam a UI confusa | Médio | Média | Começar com interface simples de lista de blocos; evoluir para grid visual se necessário |
| Cópia de período anterior com dados inválidos (ex: feriado fora do novo range) | Baixo | Média | Filtrar feriados que caem fora do novo intervalo durante a cópia |
| Performance do cálculo de dias letivos com muitos feriados e períodos longos | Baixo | Baixa | Cálculo em memória no Java, sem queries extras — complexidade O(dias_do_período) |

---

## Plano de Implementação

| Fase | Tarefa | Descrição | Status |
|------|--------|-----------|--------|
| **1 — Banco** | Extension btree_gist | Ativar extensão para EXCLUDE constraint | Pendente |
| **1 — Banco** | Migration — academic_period | Criar tabela com RLS, CHECK e EXCLUDE | Pendente |
| **1 — Banco** | Migration — academic_period_holiday | Criar tabela de feriados com FK cascade | Pendente |
| **1 — Banco** | Migration — laboratory_schedule | Criar tabela de grade semanal | Pendente |
| **1 — Banco** | Entities JPA | AcademicPeriod, AcademicPeriodHoliday, LaboratorySchedule | Pendente |
| **2 — Backend** | AcademicPeriodService | CRUD de períodos com validação de sobreposição | Pendente |
| **2 — Backend** | AcademicPeriodController | Endpoints REST de períodos e feriados | Pendente |
| **2 — Backend** | LaboratoryScheduleService | CRUD de grade de ocupação | Pendente |
| **2 — Backend** | LaboratoryScheduleController | Endpoints REST de grade | Pendente |
| **2 — Backend** | PeriodSummaryService | Cálculo de dias letivos/mês e horas/lab/mês | Pendente |
| **2 — Backend** | Copy period | Duplicar período com feriados e grades | Pendente |
| **3 — Frontend** | API client | Funções para academic-periods e schedules | Pendente |
| **3 — Frontend** | AcademicPeriodList | Listagem de períodos com ações CRUD | Pendente |
| **3 — Frontend** | AcademicPeriodForm | Dialog de criação/edição de período | Pendente |
| **3 — Frontend** | HolidayEditor | Edição de feriados dentro do detalhe do período | Pendente |
| **3 — Frontend** | ScheduleGrid | Grade de ocupação por laboratório | Pendente |
| **3 — Frontend** | PeriodSummary | Resumo de dias letivos e horas por mês | Pendente |
| **3 — Frontend** | Sidebar + rotas | Adicionar item "Calendário" e rotas | Pendente |
| **4 — Seed** | afterMigrate.sql | Adicionar período 2024.1 com feriados e grades ao seed | Pendente |

---

## Estratégia de Testes

| Tipo | Escopo | Abordagem |
|------|--------|-----------|
| **Unitário (backend)** | Cálculo de dias letivos, validações | Testes isolados do PeriodSummaryService |
| **Integração (backend)** | CRUD + RLS + sobreposição | Requisições HTTP com PostgreSQL real |

**Cenários críticos a testar:**

**Períodos:**
- Criar período com datas válidas → 201
- Criar período com data final anterior à inicial → 400
- Criar período sobrepondo outro da mesma instituição → 409
- Criar período sem sobreposição → 201
- Excluir período → cascade remove feriados e grades

**Feriados:**
- Adicionar feriado dentro do intervalo → OK
- Adicionar feriado fora do intervalo → 400
- Feriado duplicado na mesma data → ignorado (upsert)

**Grade de ocupação:**
- Criar blocos sem sobreposição → OK
- Criar blocos sobrepostos no mesmo dia → 400
- Bloco com horário final anterior ao inicial → 400
- Dia da semana fora de 1-7 → 400

**Cálculos derivados:**
- Período com 0 feriados: dias letivos = dias úteis no intervalo
- Período com feriado em dia útil: desconta 1 dia naquele mês
- Período com feriado em fim de semana: não altera contagem
- Lab com grade de 4h seg-sex: horas/mês = dias_letivos_do_mês × 4
- Lab sem grade definida: 0 horas (pendência indicada)

**Isolamento (RLS):**
- Instituição A não enxerga períodos da instituição B
- Grade de lab da instituição A não é visível para instituição B
