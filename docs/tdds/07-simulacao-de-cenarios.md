# TDD — Simulação de Cenários

| Campo            | Valor                                                          |
| ---------------- | -------------------------------------------------------------- |
| Tech Lead        | Andrey Dedey                                                   |
| PRD de origem    | `docs/prds/07-simulacao-de-cenarios.md`                        |
| ADRs relevantes  | ADR-001 (stack), ADR-004 (multi-tenancy RLS)                   |
| PRDs dependentes | PRD 03 (Equipamentos), PRD 05 (Calendário), PRD 06 (Cálculo)   |
| Status           | Draft                                                          |
| Criado em        | 2026-10-01                                                     |
| Atualizado em    | 2026-10-01                                                     |

---

## Contexto

Saber quanto um laboratório emite é útil. Saber quanto ele deixaria de emitir ao trocar os equipamentos por modelos mais eficientes é o que sustenta uma decisão de compra ou de migração de sistema operacional.

O PRD 06 já entrega o cálculo real completo. A simulação não é uma calculadora nova — ela reutiliza exatamente o mesmo motor (`EmissionCalculationService`), mas substitui as especificações reais dos equipamentos por parâmetros hipotéticos informados pelo usuário. A diferença entre o resultado real e o simulado, exibida lado a lado, é o ganho potencial da mudança.

O design no Pencil (frame "7 – Simulação") mostra uma única tela: dois cards comparativos (Cenário A — Atual vs. Cenário B — Simulado) com um card de insight abaixo mostrando a redução percentual e equivalências ecológicas.

### O que existe hoje

- `EmissionCalculationService.calculate(periodId)` — motor completo de cálculo: itera labs, equipamentos, horas de uso, fatores de emissão mensais e retorna `EmissionResultDTO`
- `LaboratoryEquipment` — vincula `Configuration` (modelo + OS + monitor) a `Laboratory` com `quantity`
- `EquipmentModel` — possui `tdpWatts` (CPU TDP) e `gpuTdpWatts` (GPU TDP, opcional)
- `Monitor` — possui `watts`
- A fórmula base: `energia_kWh = (tdp_cpu + tdp_gpu + watts_monitor) × horas × quantidade / 1000`
- Nada de `Scenario` existe ainda no backend

### Decisões resolvidas

- **Simulação no nível de watts uniformes, não por configuração.** O PRD cita substituição per-configuration, mas o design mostra "TDP médio" e "Monitor médio" como parâmetros únicos para todo o cenário. O MVP usa override uniforme: um valor hipotético de watts de computador e um de monitor substituem todas as configurações do período. Isso cobre os casos de uso centrais (trocar todo o parque por mini PCs, trocar monitores) com implementação simples. Override por configuração fica para V2.

- **Baseline sempre recomputado sob demanda.** O TDD-06 estabeleceu que o cálculo não é armazenado. A simulação segue o mesmo princípio: ao abrir um cenário, baseline e simulado são calculados na hora a partir dos dados atuais. Isso elimina a necessidade de snapshot e garante consistência com o estado real. A história "seja informado de que a base mudou" do PRD fica fora do escopo V1 — com baseline on-demand, a "base atual" é sempre a base, sem ambiguidade.

- **Simulação de SO fora do escopo V1.** O PRD levanta a dúvida sobre de onde vem o dado de consumo ao trocar o SO sem medição própria. A resposta é: a simulação opera ao nível de watts (TDP), não de SO. Se o usuário sabe que migrar para Linux reduz o TDP efetivo em X watts, ele informa esse valor diretamente. Não há fator de correção por SO embutido.

- **Período e grade de aulas não são alteráveis no cenário.** Horas de uso, dias letivos e turnos são propriedades do período letivo real. O cenário altera apenas as especificações de consumo dos equipamentos. A história de "redistribuição de horários" do PRD fica fora do escopo V1.

- **Cenário salvo por usuário, scoped por instituição via RLS.** Um cenário pertence a uma instituição (não a um usuário específico), assim como o restante dos dados. Qualquer membro da instituição pode ver e editar os cenários dela.

- **MANAGER e RESEARCHER podem criar e editar cenários.** Simulação é uma atividade de análise, não de configuração do parque. Não há razão para restringir a ADMIN.

- **Resultado da simulação não é persistido.** Apenas os parâmetros do cenário são salvos (`name`, `periodId`, `hypotheticalComputerWatts`, `hypotheticalMonitorWatts`). O resultado é recalculado a cada chamada de `/simulate`.

---

## Escopo

### Dentro do escopo

- CRUD de cenários (nome + parâmetros hipotéticos) com RLS
- Endpoint de simulação: recebe `scenarioId`, retorna baseline + simulado + delta
- Override uniforme de watts de computador e/ou monitor para todo o período
- Comparação: total, decomposição em computadores vs. monitores, diferença absoluta e percentual
- Equivalências ecológicas no delta (árvores, km de carro)
- Tela de simulação com dois cards comparativos e insight card (fiel ao design Pencil)
- Listagem de cenários por período + criação via modal ("Nova Simulação")
- Cenário pode ser criado a partir de qualquer período com cálculo disponível

### Fora do escopo

- Override por configuração (por laboratório ou por modelo específico)
- Simulação de migração de SO como parâmetro nomeado
- Alteração de período, grade de aulas ou número de estações no cenário
- Snapshot do baseline / notificação de "base mudou"
- Otimização automática de configuração
- Custo financeiro, ROI ou payback da substituição
- Emissões de fabricação e descarte (escopo 3)
- Catálogo de equipamentos do mercado integrado à simulação

---

## Solução Técnica

### Visão geral da arquitetura

```
┌─────────────────────────────────────────────────────────────┐
│  Frontend (React + Vite + shadcn/ui)                        │
│                                                             │
│  ScenarioPage: dois cards comparativos + insight card       │
│  Nova Simulação: modal (período + nome + watts hipotéticos) │
│  Sidebar: item "Simulação" → /scenarios                     │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP JSON / X-Institution-Id
┌──────────────────────────▼──────────────────────────────────┐
│  Backend                                                    │
│                                                             │
│  ScenarioController  ──→  ScenarioService                   │
│  GET /scenarios/{id}/simulate                               │
│    → carrega Scenario (parâmetros hipotéticos)              │
│    → delega a EmissionCalculationService (baseline)         │
│    → delega a ScenarioSimulationService (simulado)          │
│    → monta ScenarioComparisonDTO                            │
└──────────────────────────┬──────────────────────────────────┘
                           │ JDBC
┌──────────────────────────▼──────────────────────────────────┐
│  PostgreSQL + RLS                                           │
│  scenario                                                   │
│  UNIQUE (institution_id, name, period_id)                   │
│  RLS policy por institution_id                              │
└─────────────────────────────────────────────────────────────┘
```

### Modelo de dados

**Nova tabela `scenario`** (V19)

| Coluna                         | Tipo                | Restrições                               |
| ------------------------------ | ------------------- | ---------------------------------------- |
| `id`                           | `UUID`              | PK                                       |
| `name`                         | `VARCHAR(200)`      | NOT NULL                                 |
| `institution_id`               | `UUID`              | FK → institution(id), NOT NULL           |
| `period_id`                    | `UUID`              | FK → academic_period(id), NOT NULL       |
| `hypothetical_computer_watts`  | `INTEGER`           | NULL → mantém watts reais de cada config |
| `hypothetical_monitor_watts`   | `INTEGER`           | NULL → mantém watts reais de cada config |
| `created_at`                   | `TIMESTAMP WITH TZ` | NOT NULL                                 |
| `updated_at`                   | `TIMESTAMP WITH TZ` | NOT NULL                                 |

**Constraints:**
- `UNIQUE (institution_id, period_id, name)` — nome único por período dentro da instituição
- `RLS policy`: `institution_id = current_setting('app.current_institution', true)::uuid`

### API REST

| Método   | Rota                               | Descrição                          | Status | Permissão       |
| -------- | ---------------------------------- | ---------------------------------- | ------ | --------------- |
| `POST`   | `/api/v1/scenarios`                | Criar cenário                      | 201    | autenticado     |
| `GET`    | `/api/v1/scenarios`                | Listar cenários (filtro `periodId`)| 200    | autenticado     |
| `GET`    | `/api/v1/scenarios/{id}`           | Buscar cenário por ID              | 200    | autenticado     |
| `PUT`    | `/api/v1/scenarios/{id}`           | Atualizar nome ou parâmetros       | 200    | autenticado     |
| `DELETE` | `/api/v1/scenarios/{id}`           | Remover cenário                    | 204    | autenticado     |
| `GET`    | `/api/v1/scenarios/{id}/simulate`  | Calcular baseline + simulado       | 200    | autenticado     |

**Contrato — POST/PUT:**

```json
// Request
{
  "name": "Substituição por Mini PCs",
  "periodId": "uuid-do-periodo",
  "hypotheticalComputerWatts": 28,
  "hypotheticalMonitorWatts": 16
}

// Response 201 / 200
{
  "id": "uuid",
  "name": "Substituição por Mini PCs",
  "periodId": "uuid-do-periodo",
  "periodName": "2025.1",
  "hypotheticalComputerWatts": 28,
  "hypotheticalMonitorWatts": 16,
  "createdAt": "2026-10-01T14:30:00Z"
}
```

**Contrato — GET `/scenarios/{id}/simulate`:**

```json
{
  "scenarioId": "uuid",
  "scenarioName": "Substituição por Mini PCs",
  "periodName": "2025.1",
  "baseline": {
    "totalEmissionKg": 7971.0,
    "computerEmissionKg": 5800.0,
    "monitorEmissionKg": 2171.0,
    "totalEnergyKwh": 82500.0,
    "avgComputerWatts": 65,
    "avgMonitorWatts": 22,
    "stationCount": 54,
    "schoolDays": 88
  },
  "simulated": {
    "totalEmissionKg": 3388.0,
    "computerEmissionKg": 2100.0,
    "monitorEmissionKg": 1288.0,
    "totalEnergyKwh": 35000.0,
    "avgComputerWatts": 28,
    "avgMonitorWatts": 16
  },
  "delta": {
    "emissionKg": -4583.0,
    "emissionPct": -57.5,
    "computerEmissionKg": -3700.0,
    "monitorEmissionKg": -883.0,
    "equivalentCarKm": 27498,
    "equivalentTreesNeeded": 23.0
  }
}
```

**Regras de negócio:**
- `POST` com `name` já existente para o mesmo `(institution_id, period_id)` → 409 Conflict
- `POST/PUT` com `hypotheticalComputerWatts <= 0` ou `hypotheticalMonitorWatts <= 0` → 400
- `POST/PUT` com `name` em branco → 400
- `GET /simulate` de cenário pertencente a período sem equipamentos ou sem fatores → 422 (Unprocessable Entity), com mensagem descritiva
- `GET /simulate` com `hypotheticalComputerWatts` null → usa watts reais de cada configuração (baseline = simulado para computadores)

### Backend — Entidades e serviços

**`Scenario`** — nova entidade:

```java
@Entity
class Scenario {
    UUID id;
    String name;
    @ManyToOne AcademicPeriod period;
    @ManyToOne Institution institution;
    Integer hypotheticalComputerWatts; // null = manter reais
    Integer hypotheticalMonitorWatts;  // null = manter reais
    Instant createdAt, updatedAt;
}
```

**`ScenarioSimulationService`** — lógica central:

```
simulate(scenario):
  baseline = EmissionCalculationService.calculate(scenario.period.id)

  Para cada config no período:
    computerWatts = scenario.hypotheticalComputerWatts ?? (tdpCpu + tdpGpu)
    monitorWatts  = scenario.hypotheticalMonitorWatts  ?? monitor.watts
    totalWatts = computerWatts + monitorWatts

    para cada mês:
      energiaKwh = totalWatts × horas × qty / 1000
      emissaoKg  = energiaKwh × fator[mês]

  retorna ScenarioComparisonDTO com baseline e simulado
```

A decomposição computador vs. monitor é calculada separando as parcelas:
- `computerEmissionKg` = `computerWatts / totalWatts × emissaoKg` por config
- `monitorEmissionKg` = `monitorWatts / totalWatts × emissaoKg` por config

**`ScenarioController`** — CRUD + simulate. Sem `@PreAuthorize` — qualquer usuário autenticado.

**`ScenarioService`** — validações: nome obrigatório, watts > 0 se informados, duplicata, not-found.

### Frontend

**Rota nova:** `/scenarios` → `ScenarioPage`

O sidebar já tem área "Análise" — o item "Simulação" aponta para `/scenarios`.

**`ScenarioPage`** — tela principal (fiel ao design Pencil):
- Header: breadcrumb "Análise › Simulação", título "Simulação de Cenários", botões "Salvar" (outline) e "+ Nova Simulação" (primário verde)
- Select de cenário no header (se houver mais de um): dropdown lista os cenários da instituição
- Dois cards comparativos lado a lado (544 px cada):
  - **Card A — Atual** (badge "Baseline", fundo resultado `#EEF1EC`): EQUIPAMENTOS, TDP MÉDIO, MONITOR MÉDIO, HORAS/DIA, DIAS LETIVOS, FATOR SIN → resultado em destaque
  - **Card B — Simulado** (badge "Simulação" azul `#DBEAFE`/`#1E40AF`, fundo resultado `#EFF6FF`): mesmos campos com valores hipotéticos editáveis inline
- **Insight card** (`#DEECE2`): ícone verde, "Redução de X% nas emissões", subtítulo com kg e equivalências
- Estado vazio (sem cenários): card central com botão "Criar primeira simulação"

**Formulário "Nova Simulação"** — modal:
- Campos: Nome, Período (select dos períodos da instituição), TDP hipotético (W) com label "Watts do computador", Watts do monitor
- Watts opcionais: se deixados em branco, os valores reais do período são mantidos

**Tipos TypeScript:**

```typescript
interface ScenarioDTO {
  id: string
  name: string
  periodId: string
  periodName: string
  hypotheticalComputerWatts: number | null
  hypotheticalMonitorWatts: number | null
  createdAt: string
}

interface ScenarioComparisonDTO {
  scenarioId: string
  scenarioName: string
  periodName: string
  baseline: ScenarioSideDTO
  simulated: ScenarioSideDTO
  delta: ScenarioDeltaDTO
}

interface ScenarioSideDTO {
  totalEmissionKg: number
  computerEmissionKg: number
  monitorEmissionKg: number
  totalEnergyKwh: number
  avgComputerWatts: number
  avgMonitorWatts: number
  stationCount: number
  schoolDays: number
}

interface ScenarioDeltaDTO {
  emissionKg: number
  emissionPct: number
  computerEmissionKg: number
  monitorEmissionKg: number
  equivalentCarKm: number
  equivalentTreesNeeded: number
}
```

---

## Riscos

| Risco | Impacto | Probabilidade | Mitigação |
|-------|---------|---------------|-----------|
| Override uniforme não reflete cenários mistos (alguns labs trocam, outros não) | Médio | Média | Documentar como limitação V1; V2 adiciona override por laboratório |
| `ScenarioSimulationService` duplica lógica de `EmissionCalculationService` | Médio | Alta | Extrair método auxiliar parametrizado em `EmissionCalculationService` que recebe função de override de watts; ambos chamam o mesmo núcleo |
| Período sem fatores de emissão cadastrados quebra o simulate | Alto | Média | Retornar 422 com mensagem clara; verificar pré-requisitos antes do cálculo |
| Baseline on-demand difere do que o usuário viu quando criou o cenário | Baixo | Baixa | Aceitar — baseline sempre reflete dados atuais; isso é documentado como comportamento esperado |

---

## Plano de Implementação

| Fase | Tarefa | Descrição | Esforço |
|------|--------|-----------|---------|
| **1 — Spec** | Spec + tasks | Escrever spec formal com histórias e critérios de aceite; criar tasks.md | baixo |
| **2 — Backend** | Migration + entidade | V19: tabela `scenario` com RLS; entidade `Scenario`, repositório | baixo |
| **2 — Backend** | CRUD de cenários | `ScenarioController` + `ScenarioService` (create, list, get, update, delete) + validações | médio |
| **2 — Backend** | Simulação | `ScenarioSimulationService.simulate()` — override de watts + decomposição computador/monitor + delta | alto |
| **2 — Testes** | Testes de integração | CRUD completo (400/409/404/403), simulate com e sem override, RLS | alto |
| **2 — Testes** | Testes unitários | Service: validações de campo, duplicata, not-found; SimulationService: override correto, decomposição, delta | médio |
| **3 — Frontend** | ScenarioPage + modal | Tela comparativa (dois cards + insight), modal "Nova Simulação", integração com API | alto |
| **4 — Gate** | Verify + Audit | `onp-spec verify` + `onp-spec audit --ci` → exit 0 | — |

**Dependências:** Fase 2 backend desbloqueia Fase 3 frontend. Testes podem ser escritos antes da implementação (TDD).

---

## Estratégia de Testes

| Tipo | Escopo | Abordagem |
|------|--------|-----------|
| **Integração (backend)** | CRUD + simulate + RLS | Testcontainers + PostgreSQL real; seeds de período com equipamentos e fatores |
| **Unitário (backend)** | ScenarioService + ScenarioSimulationService | Mocks dos repositórios; foco em validações e na aritmética do override |

**Cenários a testar — integração:**

- ADMIN cria cenário com dados válidos → 201
- Cria cenário com nome duplicado para mesmo período → 409
- Cria cenário com watts negativos → 400
- Cria cenário com nome em branco → 400
- Lista cenários: retorna apenas cenários da instituição (RLS)
- Atualiza cenário existente → 200
- Remove cenário → 204
- Remove cenário inexistente → 404
- `GET /simulate` sem override: resultado simulado idêntico ao baseline
- `GET /simulate` com `hypotheticalComputerWatts = 28` (vs 65 real): emissão simulada menor
- `GET /simulate` com `hypotheticalMonitorWatts = 0` (sem monitor): parcela monitor = 0
- `GET /simulate` de cenário de instituição B por usuário de instituição A → 404 (RLS)

**Cenários a testar — unitários:**

- `create` com `name` nulo → exceção
- `create` com `watts <= 0` → exceção
- `create` com `periodId` inexistente → exceção
- `simulate` com overrides nulos usa watts reais de cada config
- `simulate` com `hypotheticalComputerWatts` definido: `computerEmissionKg` calculada corretamente
- `simulate` identidade: override igual aos watts reais → delta = 0
- `delta.emissionPct` calculado corretamente com arredondamento
