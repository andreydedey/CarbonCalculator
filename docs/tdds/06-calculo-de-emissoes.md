# TDD — Cálculo e Apresentação das Emissões

| Campo            | Valor                                          |
| ---------------- | ---------------------------------------------- |
| Tech Lead        | Andrey Dedey                                   |
| PRD de origem    | `docs/prds/06-calculo-de-emissoes.md`          |
| PRDs dependentes | PRD 03 (Equipamentos), PRD 05 (Calendário), PRD 09 (Fatores de Emissão) |
| Status           | Pendente                                       |
| Criado em        | 2026-09-29                                     |
| Atualizado em    | 2026-09-29                                     |

---

## Contexto

O sistema já tem:
- **Parque computacional** (PRDs 02/03): laboratórios, modelos de computador com TDP, monitores com potência, configurações (modelo + SO + monitor) com quantidades por lab.
- **Calendário letivo** (PRD 05): períodos, turnos, feriados, e grade de ocupação por laboratório com slots de aula.

Com isso, já é possível calcular **quanto** cada estação consome (soma de TDP do processador, GPU se houver, e monitor) e **por quanto tempo** (horas de uso por mês derivadas da grade de ocupação). O que falta é o **fator de emissão** — o multiplicador que converte kWh em kgCO₂ — e o motor de cálculo que junta tudo.

O PRD 04 (Medições de Consumo) ainda não foi implementado. Por isso, o cálculo usará inicialmente as especificações de fabricante (TDP) como fonte de consumo. A arquitetura será extensível para, quando medições existirem, preferí-las sobre especificações — mas isso não entra neste escopo.

O PRD 09 (Fatores de Emissão) será parcialmente implementado neste TDD: a tabela de fatores e o CRUD necessário para o cálculo. A gestão completa (visualização pública, cobertura de meses) fica para o TDD do PRD 09.

### Decisões resolvidas

- **O consumo de cada configuração é a soma das especificações dos componentes.** `consumo_watts = tdp_cpu + tdp_gpu (se houver) + watts_monitor (se houver)`. Quando medições forem implementadas (PRD 04), elas terão prioridade.
- **Configuração sem monitor tem consumo parcial.** O resultado indica que o consumo de monitores não está contabilizado para aquela configuração. O cálculo continua, não bloqueia.
- **Cada mês usa seu próprio fator de emissão.** O período pode abranger vários meses; o fator muda mês a mês conforme publicação do MCTI.
- **Sistemas isolados vs. SIN.** Há dois tipos de fator. Neste TDD, todas as instituições usam o fator SIN (Sistema Interligado Nacional). O suporte a sistemas isolados fica para o PRD 09 completo.
- **O cálculo é sob demanda, não armazenado.** É computado a cada requisição a partir dos dados cadastrados. Não há tabela de resultados persistidos. Isso garante que o resultado reflete sempre os dados atuais.
- **Equivalências do cotidiano são fixas.** Usam os valores do estudo de referência (km rodados de carro, árvores necessárias). Não são configuráveis nesta versão.
- **Exportação em CSV.** O formato mais simples que atende pesquisadores e gestores. PDF fica fora do escopo.

## Definição do Problema

O sistema conhece o parque computacional e o calendário letivo, mas não consegue responder "quanto CO₂ este laboratório emite em um semestre". Falta:

1. A tabela de fatores de emissão (kgCO₂/kWh por mês)
2. O motor de cálculo que combina consumo × horas × fator
3. A interface que apresenta o resultado decomposto nas dimensões úteis para decisão

**O que acontece se não resolvermos:**
- O objetivo central do TCC (calcular e comparar emissões) não pode ser demonstrado
- Não há como validar o resultado contra o estudo de referência (767 kg CO₂)
- Os dados cadastrados ficam sem propósito prático

## Escopo

### Dentro do escopo

- Tabela `emission_factor` com fator mensal do SIN (global, não por instituição)
- CRUD de fatores de emissão (admin only)
- Endpoint de cálculo de emissões por período letivo
- Cálculo por laboratório, com decomposição por: mês, turno, dia da semana
- Decomposição computador vs. monitor por configuração
- Ranking de modelos de computador, monitores e sistemas operacionais por contribuição
- Validação de pré-requisitos (todos os meses cobertos por fator, todas as configurações com consumo)
- Equivalências do cotidiano (km de carro, árvores)
- Transparência: mostrar dados de entrada, origem do consumo e fator usado
- Exportação CSV dos resultados
- Tela de dashboard de emissões com gráficos
- Seed de fatores de emissão para desenvolvimento

### Fora do escopo

- Medições físicas de consumo (PRD 04) — usa especificações de fabricante
- Fator de sistemas isolados (PRD 09 completo)
- Persistência/cache do resultado calculado
- Simulação de cenários (PRD 07)
- Acompanhamento longitudinal / comparação entre períodos (PRD 08)
- Exportação em PDF
- Recomendações automáticas de mitigação
- Emissões de escopo 1 e 3
- Conversão monetária do consumo

---

## Solução Técnica

### Visão geral da arquitetura

```
┌──────────────────────────────────────────────────────────┐
│  Frontend (React)                                        │
│                                                          │
│  pages/emissions          ──→  Dashboard de emissões     │
│  pages/emission-factors   ──→  CRUD fatores (admin)      │
│                                                          │
│  Componentes: EmissionsDashboard, EmissionsByMonth,      │
│               EmissionsByLab, BreakdownCards,             │
│               EquivalenceCards, EmissionFactorsPage       │
└──────────────────────────┬───────────────────────────────┘
                           │ HTTP (JSON)
┌──────────────────────────▼───────────────────────────────┐
│  Backend (Spring Boot)                                   │
│                                                          │
│  EmissionFactorController → EmissionFactorService        │
│  EmissionController       → EmissionCalculationService   │
│                                                          │
│  Motor de cálculo:                                       │
│    1. Resolve consumo por configuração (TDP)             │
│    2. Calcula horas por lab/mês (do PeriodSummaryService)│
│    3. Multiplica consumo × horas × fator = emissão      │
│    4. Agrega nas dimensões de saída                      │
└──────────────────────────┬───────────────────────────────┘
                           │ JDBC + RLS
┌──────────────────────────▼───────────────────────────────┐
│  PostgreSQL                                              │
│  emission_factor (global, sem RLS)                       │
│  + tabelas existentes (configuration, laboratory,        │
│    equipment_model, monitor, laboratory_equipment,       │
│    academic_period, academic_period_shift,                │
│    laboratory_schedule)                                   │
└──────────────────────────────────────────────────────────┘
```

### Modelo de dados

**Tabela `emission_factor`** (nova — global, SEM RLS)

| Coluna        | Tipo                | Restrições                                       |
| ------------- | ------------------- | ------------------------------------------------ |
| `id`          | `UUID`              | PK, gerado automaticamente                      |
| `year`        | `SMALLINT`          | NOT NULL                                         |
| `month`       | `SMALLINT`          | NOT NULL, CHECK (1..12)                          |
| `value`       | `NUMERIC(10,6)`     | NOT NULL, CHECK (> 0) — kgCO₂/kWh               |
| `source`      | `VARCHAR(500)`      | NOT NULL — referência da publicação oficial       |
| `created_at`  | `TIMESTAMP WITH TZ` | NOT NULL                                         |
| `updated_at`  | `TIMESTAMP WITH TZ` | NOT NULL                                         |

**Constraints:**
- UNIQUE(year, month) — um fator por mês
- CHECK: `month BETWEEN 1 AND 12`
- CHECK: `value > 0`
- SEM RLS — os fatores são globais (publicados pelo MCTI para todo o SIN)

**Índices:**
- `idx_emission_factor_year_month` — (year, month) para busca rápida

### Fórmula do cálculo

O cálculo segue a cadeia:

```
Para cada laboratório no período:
  Para cada configuração do laboratório:
    consumo_watts = tdp_cpu
                  + tdp_gpu (se houver)
                  + watts_monitor (se houver)

    Para cada mês do período:
      horas_uso = horas do lab naquele mês (já calculado pelo PeriodSummaryService)
      energia_kwh = consumo_watts × horas_uso × quantidade_estacoes / 1000
      emissao_kg = energia_kwh × fator_emissao_do_mes
```

**Decomposições derivadas:**
- **Por mês**: soma das emissões de todas as configurações de todos os labs naquele mês
- **Por laboratório**: soma de todos os meses do lab
- **Por turno**: proporcional às horas de cada turno (calculável a partir da grade)
- **Por dia da semana**: proporcional às horas de cada dia (calculável a partir da grade)
- **Computador vs. monitor**: calculado separadamente para cada configuração
- **Por modelo de computador**: agrupado por `equipment_model.id`
- **Por modelo de monitor**: agrupado por `monitor.id`
- **Por sistema operacional**: agrupado por `configuration.operating_system`

### API REST

**Fatores de Emissão** (global — sem `X-Institution-Id`)

| Método | Rota                                  | Descrição                          | Acesso |
| ------ | ------------------------------------- | ---------------------------------- | ------ |
| GET    | `/api/v1/emission-factors`            | Listar fatores (paginado, filtro por ano) | Todos  |
| POST   | `/api/v1/emission-factors`            | Criar fator                        | ADMIN  |
| PUT    | `/api/v1/emission-factors/{id}`       | Atualizar fator                    | ADMIN  |
| DELETE | `/api/v1/emission-factors/{id}`       | Excluir fator                      | ADMIN  |

**Cálculo de Emissões** (requer `X-Institution-Id`)

| Método | Rota                                                            | Descrição                                       | Acesso     |
| ------ | --------------------------------------------------------------- | ------------------------------------------------ | ---------- |
| GET    | `/api/v1/academic-periods/{periodId}/emissions`                 | Calcular emissões do período (resultado completo)| RESEARCHER |
| GET    | `/api/v1/academic-periods/{periodId}/emissions/export`          | Exportar resultado em CSV                        | RESEARCHER |
| GET    | `/api/v1/academic-periods/{periodId}/emissions/readiness`       | Verificar pré-requisitos do cálculo              | RESEARCHER |

### Contratos da API

```json
// POST /api/v1/emission-factors
// Request
{
  "year": 2025,
  "month": 3,
  "value": 0.0425,
  "source": "MCTI — Fator médio de emissão de CO₂ pela geração de energia elétrica no SIN, mar/2025"
}

// Response 201
{
  "id": "...",
  "year": 2025,
  "month": 3,
  "value": 0.0425,
  "source": "MCTI — ...",
  "createdAt": "2026-09-29T10:00:00Z"
}
```

```json
// GET /api/v1/academic-periods/{periodId}/emissions/readiness
// Response 200
{
  "ready": false,
  "missingEmissionFactors": ["2025-06", "2025-07"],
  "laboratoriesWithoutEquipment": [],
  "laboratoriesWithoutSchedule": ["LABIA"],
  "configurationsWithoutConsumption": [],
  "configurationsWithoutMonitor": [
    {
      "configurationId": "...",
      "label": "Dell OptiPlex 7090 + Windows 10",
      "laboratoryNames": ["LABCOMP-01", "LABIA"]
    }
  ]
}
```

```json
// GET /api/v1/academic-periods/{periodId}/emissions
// Response 200
{
  "period": {
    "id": "...",
    "name": "2025.1",
    "startDate": "2025-03-10",
    "endDate": "2025-07-18"
  },
  "totalEmissionKg": 767.42,
  "totalEnergyKwh": 18045.2,
  "equivalences": {
    "carKm": 4604,
    "treesNeeded": 5.3
  },
  "byMonth": [
    {
      "month": "2025-03",
      "energyKwh": 2840.5,
      "emissionKg": 120.72,
      "emissionFactor": 0.0425,
      "schoolDays": 16
    }
  ],
  "byLaboratory": [
    {
      "laboratoryId": "...",
      "laboratoryName": "LABCOMP-01",
      "energyKwh": 8500.0,
      "emissionKg": 361.25,
      "stationCount": 30,
      "computerEmissionKg": 285.0,
      "monitorEmissionKg": 76.25,
      "byMonth": [
        { "month": "2025-03", "energyKwh": 1350.0, "emissionKg": 57.37 }
      ],
      "configurations": [
        {
          "configurationId": "...",
          "label": "Dell OptiPlex 7090 + Windows 10 + Dell P2422H 24\"",
          "quantity": 30,
          "consumptionWatts": 86,
          "consumptionSource": "specification",
          "computerWatts": 65,
          "monitorWatts": 21,
          "energyKwh": 8500.0,
          "emissionKg": 361.25
        }
      ]
    }
  ],
  "byShift": [
    { "shiftType": "MORNING", "energyKwh": 6000.0, "emissionKg": 255.0 },
    { "shiftType": "AFTERNOON", "energyKwh": 12045.2, "emissionKg": 512.42 }
  ],
  "byDayOfWeek": [
    { "dayOfWeek": 1, "label": "Segunda", "energyKwh": 3800.0, "emissionKg": 161.5 }
  ],
  "byEquipmentModel": [
    { "modelId": "...", "modelName": "Dell OptiPlex 7090", "emissionKg": 500.0, "percentage": 65.1 }
  ],
  "byMonitorModel": [
    { "monitorId": "...", "monitorName": "Dell P2422H 24\"", "emissionKg": 100.0, "percentage": 13.0 }
  ],
  "byOperatingSystem": [
    { "operatingSystem": "Windows 10", "emissionKg": 400.0, "percentage": 52.1 },
    { "operatingSystem": "Linux", "emissionKg": 367.42, "percentage": 47.9 }
  ],
  "inputs": {
    "emissionFactors": [
      { "month": "2025-03", "value": 0.0425, "source": "MCTI — ..." }
    ],
    "consumptionSources": [
      {
        "configurationId": "...",
        "label": "Dell OptiPlex 7090 + Windows 10 + Dell P2422H",
        "source": "specification",
        "computerWatts": 65,
        "monitorWatts": 21,
        "totalWatts": 86
      }
    ]
  }
}
```

```
// GET /api/v1/academic-periods/{periodId}/emissions/export
// Response 200 (text/csv)
// Headers: Content-Disposition: attachment; filename="emissoes-2025.1.csv"

Laboratório,Mês,Energia (kWh),Emissão (kgCO₂),Fator (kgCO₂/kWh),Dias Letivos
LABCOMP-01,2025-03,1350.00,57.37,0.0425,16
LABCOMP-01,2025-04,1800.00,81.00,0.0450,20
...
```

### Equivalências do cotidiano

Valores fixos usados pelo estudo de referência:

| Equivalência | Fórmula | Fonte |
|---|---|---|
| Km rodados de carro | `emissao_kg / 0.1667` | Emissão média por km de carro no Brasil (MMA) |
| Árvores necessárias para absorver | `emissao_kg / 145.14` | Absorção anual média de uma árvore urbana |

### Frontend

**Páginas:**

| Página              | Rota                    | Descrição                                                |
| ------------------- | ----------------------- | -------------------------------------------------------- |
| Dashboard Emissões  | `/emissions`            | Seleção de período, resultado do cálculo com gráficos    |
| Fatores de Emissão  | `/emission-factors`     | Tabela de fatores com CRUD (admin only)                  |

**Componentes principais:**

- `EmissionsDashboard` — página principal: select de período, cards de total + equivalências, gráficos de decomposição
- `ReadinessCheck` — alerta de pré-requisitos não atendidos (meses sem fator, labs sem grade, etc.)
- `EmissionsByMonth` — gráfico de barras com emissão por mês (Recharts ou similar)
- `EmissionsByLab` — tabela/gráfico de emissão por laboratório
- `BreakdownCards` — cards mostrando computador vs. monitor, top modelos, top SOs
- `EquivalenceCards` — cards visuais com as equivalências (km de carro, árvores)
- `TransparencyPanel` — colapsável mostrando inputs usados, fontes de consumo e fatores
- `EmissionFactorsPage` — tabela de fatores com filtro por ano, formulário de criação/edição
- `ExportButton` — botão de download do CSV

**Navegação:**
- Novo item na sidebar: "Emissões" (ícone `leaf`), visível para todos, na seção "Análise"
- Novo item na sidebar: "Fatores de Emissão" (ícone `percent`), visível apenas para admin, na seção "Configuração"
- Breadcrumb: "Análise › Emissões" na dashboard

**Bibliotecas de gráficos:**
- Recharts (já compatível com React, leve, boa integração com shadcn/ui). Instalar como dependência.

### Dependências

**Backend:**
- Nenhuma nova. Usa Spring Data JPA, Flyway, PostgreSQL (existentes).

**Frontend:**
- `recharts` — biblioteca de gráficos React (nova dependência).

---

## Riscos

| Risco | Impacto | Probabilidade | Mitigação |
|-------|---------|---------------|-----------|
| Consumo por TDP superestima o real | Médio | Alta | O TDP é o valor máximo; o consumo real é menor. Sinalizar como "estimativa por especificação" e indicar que medição (PRD 04) refinaria o resultado |
| Fatores de emissão do MCTI não disponíveis para todos os meses | Alto | Média | Endpoint `readiness` verifica antes do cálculo e indica quais meses faltam |
| Performance do cálculo em instituições com muitos labs/configurações | Baixo | Baixa | Cálculo em memória no Java, sem queries N+1 — busca tudo em batch e agrega |
| Resultado diverge do estudo de referência | Médio | Média | Seed inclui dados que reproduzem o cenário do estudo; testar que o cálculo bate |
| Gráficos pesados no frontend com muitos dados | Baixo | Baixa | Recharts é otimizado; dados agregados pelo backend (não envia granularidade desnecessária) |

---

## Plano de Implementação

| Fase | Tarefa | Descrição | Status |
|------|--------|-----------|--------|
| **1 — Banco** | Migration — emission_factor | Criar tabela global sem RLS | Pendente |
| **1 — Banco** | Entity JPA | EmissionFactor com year, month, value, source | Pendente |
| **2 — Backend** | EmissionFactorService | CRUD com validação de duplicidade (year+month) | Pendente |
| **2 — Backend** | EmissionFactorController | Endpoints REST (admin: CUD; todos: R) | Pendente |
| **2 — Backend** | EmissionCalculationService | Motor de cálculo: consumo × horas × fator com decomposições | Pendente |
| **2 — Backend** | EmissionController | Endpoints: cálculo, readiness, export CSV | Pendente |
| **3 — Frontend** | API client | Funções para emission-factors e emissions | Pendente |
| **3 — Frontend** | Sidebar + rotas | Adicionar "Emissões" e "Fatores de Emissão" na sidebar e rotas | Pendente |
| **3 — Frontend** | EmissionFactorsPage | Tabela de fatores com CRUD (admin) | Pendente |
| **3 — Frontend** | EmissionsDashboard | Dashboard com select de período, cards, gráficos, transparência | Pendente |
| **3 — Frontend** | Exportação CSV | Botão que chama o endpoint de export | Pendente |
| **4 — Seed** | afterMigrate.sql | Fatores de emissão 2025 (jan–jul) com valores reais do MCTI | Pendente |

---

## Estratégia de Testes

| Tipo | Escopo | Abordagem |
|------|--------|-----------|
| **Unitário (backend)** | Motor de cálculo, equivalências, decomposições | Testes isolados do EmissionCalculationService com dados fixos |
| **Integração (backend)** | CRUD fatores + cálculo end-to-end | Requisições HTTP com PostgreSQL real e seed completo |

**Cenários críticos a testar:**

**Fatores de emissão:**
- Criar fator com dados válidos → 201
- Criar fator para mês que já tem fator → 409
- Criar fator sem source → 400
- Criar fator com value ≤ 0 → 400
- Listar fatores filtrados por ano → paginação correta
- Atualizar fator existente → 200
- Excluir fator → 204
- Usuário não-admin tenta criar fator → 403

**Readiness (pré-requisitos):**
- Período com todos os meses cobertos e labs com grade → ready: true
- Período com mês sem fator de emissão → ready: false, lista meses faltantes
- Lab sem grade de ocupação → ready: false, lista lab
- Lab sem equipamentos → ready: false, lista lab
- Configuração sem monitor → ready: true, mas lista na seção de avisos

**Cálculo de emissões:**
- Lab com 1 configuração, 1 mês → resultado correto
- Lab com múltiplas configurações → soma corretamente
- Período com múltiplos meses e fatores diferentes → cada mês usa seu fator
- Lab sem grade → emissão zero para aquele lab
- Configuração sem monitor → calcula só computador, sinaliza
- Configuração com GPU → inclui gpuTdpWatts na soma
- Decomposição computador vs. monitor → soma das partes = total
- Decomposição por modelo → soma = total por lab
- Decomposição por SO → soma = total
- Equivalências → valores corretos com fórmulas fixas
- Período com feriado → dias letivos descontados corretamente (via PeriodSummaryService)

**Exportação CSV:**
- Download retorna Content-Type text/csv
- Dados batem com os do endpoint JSON

**Isolamento:**
- Instituição A não vê emissões de labs da instituição B
- Fatores de emissão são globais (visíveis para todos)

---

## Questões em aberto

| # | Questão | Status |
|---|---------|--------|
| 1 | As equivalências devem ser fixas ou configuráveis? | Resolvida: fixas no V1 |
| 2 | Mostrar faixa de incerteza quando consumo vem de TDP? | Resolvida: fora do escopo V1 |
| 3 | O resultado deve ter "validade" ou ficar sempre atualizado? | Resolvida: sempre recalculado (sem persistência) |
| 4 | Design das telas de emissões no Pencil | Pendente: criar antes do frontend |
