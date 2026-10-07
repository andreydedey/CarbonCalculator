# TDD — Registro e Uso de Medições de Consumo

| Campo                       | Valor                                                                          |
| --------------------------- | ------------------------------------------------------------------------------ |
| Tech Lead                   | Andrey Dedey                                                                   |
| PRD de origem               | `docs/prds/04-medicoes-de-consumo.md`                                          |
| PRDs dependentes            | PRD 03 (Equipamentos) — concluído                                              |
| PRDs afetados à frente      | PRD 06 (Cálculo), PRD 07 (Simulação) — integração com medições                |
| ADRs relevantes             | ADR-001 (stack), ADR-004 (multi-tenancy RLS)                                   |
| Status                      | Draft                                                                          |
| Criado em                   | 2026-10-04                                                                     |
| Atualizado em               | 2026-10-04                                                                     |

---

## Contexto

O sistema já tem:
- **Parque computacional** (PRD 03): modelos de computador com TDP, monitores com potência nominal, configurações (modelo + SO + monitor) com quantidades por laboratório.
- **Calendário letivo** (PRD 05): períodos, turnos, feriados e grade de ocupação.
- **Cálculo de emissões** (PRD 06): motor que usa exclusivamente o TDP dos componentes como fonte de consumo, marcando todos os resultados como "estimativa por especificação".

Sutton-Parker demonstrou que ferramentas de software superestimam o consumo real entre 48% e 58% frente ao wattímetro, por usarem tabelas de consumo desatualizadas, amostrar com menos frequência e não enxergarem os monitores conectados. A base metodológica do projeto é explícita: **o consumo deve vir de medição física ou de especificação de hardware, nunca de software de monitoramento**.

Um wattímetro de tomada mede o que está ligado a ele. Três situações surgem na prática:

- **Computador isolado** — a medição vale para aquele modelo naquele sistema operacional. O mesmo Dell OptiPlex consumiu ~2× mais no Windows 10 do que no Windows 11 no estudo de referência, então uma medição em um SO não serve para outro.
- **Monitor isolado** — a medição vale para aquele modelo de monitor em qualquer configuração que o use.
- **Conjunto (computador + monitor na mesma tomada)** — a medição vale apenas para a combinação exata de modelo, SO e monitor.

Com este PRD, o cálculo de emissões passa a ter duas fontes possíveis por componente — medição ou especificação — com **prioridade para a medição**. O campo `consumptionSource` no resultado do cálculo (já existente, com valor `"specification"`) passa a refletir a origem real de cada valor.

### Decisões resolvidas

- **Alvo da medição é o modelo, não a unidade física.** O estudo mediu cada unidade individualmente e encontrou variação de até 3× entre unidades do mesmo modelo. A plataforma permite guardar várias medições para o mesmo alvo — a variação entre unidades fica visível, e o gestor escolhe qual usar no cálculo.
- **Computador medido sempre em um sistema operacional específico.** Uma medição em um SO não se aplica a outro. O campo `operatingSystem` é obrigatório em medições de tipo COMPUTER.
- **Medição COMPUTER é independente do monitor — fundamentada na metodologia.** Sutton-Parker (2022) e o protocolo Energy Star medem o dispositivo isolado, sem periféricos conectados, para evitar que cargas externas alterem a leitura. O artigo FACOMP exigiu que cada computador fosse o único equipamento ligado ao nobreak durante a medição. O modelo aditivo (Sutton-Parker somou o consumo de um monitor medido separadamente ao consumo dos computadores) confirma que as unidades são independentes. Uma medição COMPUTER, portanto, vale para todas as configurações que compartilham aquele (modelo + SO), independentemente do monitor vinculado.
- **Medição COMBINED existe para cobrir um edge case real.** Em alguns desktops, ligar ou desligar o monitor altera levemente o consumo da GPU (carga no barramento PCIe/DisplayPort). A medição COMBINED captura esse efeito, que as medições separadas não conseguem detectar. Por isso o tipo existe — não como alternativa de conveniência, mas como solução para ambientes onde o comportamento conjunto é relevante.
- **Medição COMBINED usa 3 FKs, não `configuration_id`.** Usar `equipment_model_id` + `operating_system_id` + `monitor_id` diretamente permite registrar medições de combinações que ainda não foram vinculadas a nenhum laboratório. A integridade referencial está nas FKs individuais. Não depende de a `Configuration` existir.
- **Ponto em aberto — artigo FACOMP não explicita se o monitor estava ligado durante as medições.** Isso afeta a interpretação dos 767 kg CO₂ do estudo: se o monitor estava conectado ao nobreak, o consumo já está embutido no valor medido do computador; se não estava, o consumo do monitor foi somado separadamente. A plataforma documenta essa ambiguidade no seed de dados do estudo de referência, mas não a resolve automaticamente.
- **Data da medição é para rastreabilidade.** O fator de emissão aplicado depende dos meses do período letivo calculado, não da data da medição.
- **Medição conjunta não pode ser somada a medições separadas no mesmo cálculo.** Quando ambas existem para a mesma configuração, o gestor escolhe explicitamente qual usar. COMBINED tem prioridade no default.
- **Outlier é alerta, não bloqueio.** Se uma nova medição difere mais de 50% da média das existentes para o mesmo alvo, a plataforma alerta, mas o registro é permitido. O alerta só é gerado quando há pelo menos 2 medições anteriores.
- **A plataforma sugere o protocolo do estudo como padrão.** Ao registrar, os campos de duração (12 min) e intervalo entre leituras (4 min) têm valores sugeridos. O gestor pode informar valores diferentes. A sugestão aumenta a comparabilidade entre instituições.
- **Brilho do monitor — fora do escopo V1.** Se relevante, documentar em `conditions` (campo de texto livre).
- **Consumo em estado ocioso — fora do escopo.** O modelo assume que a medição representa uso típico de aula, seguindo o protocolo do estudo de referência.

---

## Definição do Problema

O motor de cálculo (PRD 06) já existe e funciona, mas usa exclusivamente TDP dos componentes como fonte de consumo. Todo resultado é marcado como "estimativa por especificação". Isso é insuficiente porque:

1. TDP é o consumo **máximo** do processador, não o consumo real da estação. O sistema superestima.
2. A metodologia exige que medições físicas, quando existirem, substituam as especificações.
3. Para validar o sistema contra o estudo de referência (767 kg CO₂), é necessário reproduzir as mesmas entradas — o que inclui os valores reais medidos por wattímetro, não os TDPs de folha de dados.

**O que acontece se não resolvermos:**
- O cálculo permanece com entrada de dados fraca (TDP ≠ consumo real), tornando os resultados metodologicamente questionáveis.
- Não é possível validar o sistema contra os dados do estudo de referência.
- Os critérios de aceite do PRD 04 nunca são satisfeitos.

---

## Escopo

### Dentro do escopo

- Refatoração: tabela `operating_system` substituindo o campo VARCHAR livre em `configuration`; CRUD de SOs por instituição
- Entidade `ConsumptionMeasurement` com três tipos de alvo: COMPUTER, MONITOR, COMBINED — sem `configuration_id`, usando 3 colunas de alvo (`equipment_model_id`, `operating_system_id`, `monitor_id`)
- CRUD completo de medições (criar, listar, atualizar, excluir)
- Validação de campos obrigatórios por tipo de alvo: `equipmentModelId` + `operatingSystem` para COMPUTER; `monitorId` para MONITOR; todos os três para COMBINED
- Detecção de discrepância ao criar ou atualizar: alerta quando nova medição difere mais de 50% da média das existentes para o mesmo alvo (calculado somente com 2+ medições anteriores)
- Listagem de medições com filtros por tipo, modelo, SO e monitor (paginada)
- Integração com `EmissionCalculationService`: `ConsumptionResolver` aplica hierarquia de fontes por configuração
- Hierarquia de fontes: COMBINED > (COMPUTER + MONITOR) > combinação mista (uma medição + uma spec) > especificação pura
- Seleção de medição no cálculo: default = mais recente por data; opção AVERAGE; opção EXPLICIT com ID específico por alvo
- Campo `consumptionSource` atualizado no resultado do cálculo para refletir a origem real por componente
- Endpoint POST `/api/v1/academic-periods/{id}/emissions` para cálculo com seleção explícita de medições (GET existente mantido com comportamento default)
- Tela de medições integrada às páginas de modelos de computador (aba "Medições")
- Tela de medições integrada às páginas de monitores (aba "Medições")
- Dialog de cadastro de medição com campos condicionais por tipo e valores sugeridos de protocolo
- Atualização do `TransparencyPanel` na dashboard de emissões para exibir origem por componente

### Fora do escopo

- Coleta automática de consumo por software instalado nas máquinas
- Medição de componentes internos do computador em separado (processador, GPU individualmente)
- Integração direta com wattímetros ou medidores inteligentes
- Leitura contínua ou em tempo real
- Estimativa de consumo por benchmarks sintéticos
- Importação de bases públicas de consumo por modelo
- Brilho do monitor como campo estruturado (V2)
- Consumo em estado ocioso ou desligado

---

## Solução Técnica

### Visão geral da arquitetura

```
┌──────────────────────────────────────────────────────────┐
│  Frontend (React)                                        │
│                                                          │
│  EquipmentModelPage → aba "Medições"                     │
│  MonitorPage        → aba "Medições"                     │
│  EmissionsDashboard → TransparencyPanel (atualizado)     │
│                                                          │
│  Dialog "Registrar Medição" (compartilhado)              │
└──────────────────────────┬───────────────────────────────┘
                           │ HTTP (JSON)
                           │ Header: X-Institution-Id
┌──────────────────────────▼───────────────────────────────┐
│  Backend (Spring Boot)                                   │
│                                                          │
│  ConsumptionMeasurementController                        │
│    → ConsumptionMeasurementService (CRUD + outlier)      │
│                                                          │
│  EmissionCalculationService (atualizado)                 │
│    → ConsumptionResolver (novo)                          │
│      Hierarquia: medição COMBINED > COMPUTER+MONITOR        │
│                  > mista > especificação                 │
└──────────────────────────┬───────────────────────────────┘
                           │ JDBC + RLS
┌──────────────────────────▼───────────────────────────────┐
│  PostgreSQL                                              │
│  operating_system (nova, com RLS)                        │
│  consumption_measurement (nova, com RLS)                 │
│  configuration (refatorada: operating_system → FK)       │
│  + equipment_model, monitor (existentes)                 │
└──────────────────────────────────────────────────────────┘
```

### Modelo de dados

**Tabela `operating_system`** (nova — escopo da instituição, com RLS)

Substitui o campo `VARCHAR` livre por entidade gerenciada, eliminando inconsistências de digitação e permitindo match por UUID no resolver.

| Coluna           | Tipo           | Restrições                     |
| ---------------- | -------------- | ------------------------------ |
| `id`             | `UUID`         | PK, gerado automaticamente    |
| `institution_id` | `UUID`         | FK → institution(id), NOT NULL |
| `name`           | `VARCHAR(100)` | NOT NULL                       |

**Constraints:**
- UNIQUE: `(institution_id, name)` — sem duplicatas por instituição
- RLS: policy filtrando por `current_setting('app.current_institution', true)::uuid`

**Refatoração da tabela `configuration` (existente):**
- Substituir coluna `operating_system VARCHAR(100)` por `operating_system_id UUID FK → operating_system(id)`
- Migration com backfill: extrair valores distintos de `configuration.operating_system` → popular `operating_system` → adicionar FK → migrar → drop coluna

---

**Tabela `consumption_measurement`** (nova — escopo da instituição, com RLS)

| Coluna                     | Tipo                | Restrições                                           |
| -------------------------- | ------------------- | ---------------------------------------------------- |
| `id`                       | `UUID`              | PK, gerado automaticamente                          |
| `institution_id`           | `UUID`              | FK → institution(id), NOT NULL                       |
| `target_type`              | `VARCHAR(20)`       | NOT NULL, CHECK IN ('COMPUTER', 'MONITOR', 'COMBINED') |
| `equipment_model_id`       | `UUID`              | FK → equipment_model(id), nullable                  |
| `operating_system_id`      | `UUID`              | FK → operating_system(id), nullable                 |
| `monitor_id`               | `UUID`              | FK → monitor(id), nullable                          |
| `average_watts`            | `NUMERIC(8,2)`      | NOT NULL, CHECK (> 0)                               |
| `duration_minutes`         | `INTEGER`           | NOT NULL, CHECK (> 0)                               |
| `reading_interval_minutes` | `INTEGER`           | NULL — opcional                                      |
| `measurement_date`         | `DATE`              | NOT NULL                                             |
| `conditions`               | `TEXT`              | NULL — o que estava rodando e conectado ao wattímetro|
| `notes`                    | `TEXT`              | NULL — observações adicionais                        |
| `created_at`               | `TIMESTAMP WITH TZ` | NOT NULL                                             |
| `updated_at`               | `TIMESTAMP WITH TZ` | NOT NULL                                             |

O tipo de alvo é determinado pela presença dos campos:

| Tipo | `equipment_model_id` | `operating_system_id` | `monitor_id` |
| ---- | -------------------- | --------------------- | ------------ |
| COMPUTER | NOT NULL | NOT NULL | NULL |
| MONITOR | NULL | NULL | NOT NULL |
| COMBINED | NOT NULL | NOT NULL | NOT NULL |

**Constraint de integridade por tipo de alvo:**

```sql
CONSTRAINT chk_measurement_target_fields CHECK (
  (target_type = 'COMPUTER'
    AND equipment_model_id IS NOT NULL
    AND operating_system_id IS NOT NULL
    AND monitor_id IS NULL)
  OR
  (target_type = 'MONITOR'
    AND monitor_id IS NOT NULL
    AND equipment_model_id IS NULL
    AND operating_system_id IS NULL)
  OR
  (target_type = 'COMBINED'
    AND equipment_model_id IS NOT NULL
    AND operating_system_id IS NOT NULL
    AND monitor_id IS NOT NULL)
)
```

**RLS:** Habilitado com policy filtrando por `current_setting('app.current_institution', true)::uuid`. Mesmo padrão das demais entidades da instituição.

**Índices:**

- `idx_cm_institution_id` — (institution_id) — listagem geral
- `idx_cm_computer_target` — (equipment_model_id, operating_system_id) WHERE target_type = 'COMPUTER'
- `idx_cm_monitor_target` — (monitor_id) WHERE target_type = 'MONITOR'
- `idx_cm_combined_target` — (equipment_model_id, operating_system_id, monitor_id) WHERE target_type = 'COMBINED'

### Lógica de resolução de fonte (ConsumptionResolver)

Para cada configuração (equipment_model + operating_system + monitor) no cálculo, a hierarquia de prioridade é:

```
1. Medição COMBINED para (equipment_model_id + operating_system_id + monitor_id):
   → totalWatts = average_watts da medição conjunta
   → computerWatts = null, monitorWatts = null (não decompostos)
   → source = "measurement_combined"

2. Medição COMPUTER para (equipment_model_id + operating_system_id) + Medição MONITOR para monitor_id:
   → computerWatts = average_watts da medição COMPUTER
   → monitorWatts = average_watts da medição MONITOR
   → source = "measurement_computer+measurement_monitor"

3. Medição COMPUTER para (equipment_model_id + operating_system_id) + Especificação MONITOR (monitor.watts):
   → computerWatts = average_watts da medição COMPUTER
   → monitorWatts = monitor.watts (spec)
   → source = "measurement_computer+spec_monitor"

4. Especificação COMPUTER (equipment_model.tdp_watts + gpu_tdp_watts) + Medição MONITOR para monitor_id:
   → computerWatts = tdp_watts + gpu_tdp_watts (spec)
   → monitorWatts = average_watts da medição MONITOR
   → source = "spec_computer+measurement_monitor"

5. Especificação pura (nenhuma medição disponível):
   → computerWatts = tdp_watts + gpu_tdp_watts
   → monitorWatts = monitor.watts
   → source = "specification"
```

**Quando há múltiplas medições para o mesmo alvo:**

| Estratégia     | Comportamento                                               |
| -------------- | ----------------------------------------------------------- |
| `LATEST` (default) | Usa a medição com `measurement_date` mais recente      |
| `AVERAGE`      | Usa a média aritmética de `average_watts` de todas        |
| `EXPLICIT`     | Usa o `measurementId` específico fornecido por alvo       |

O `ConsumptionResolver` recebe a lista de todas as medições da instituição em batch (evita N+1) e aplica a estratégia por alvo antes do cálculo.

### API REST

**Medições de consumo** (requer `X-Institution-Id`)

| Método | Rota                                      | Descrição                                     | Acesso     |
| ------ | ----------------------------------------- | --------------------------------------------- | ---------- |
| POST   | `/api/v1/consumption-measurements`        | Registrar medição                             | MANAGER    |
| GET    | `/api/v1/consumption-measurements`        | Listar medições (filtros + paginação)         | RESEARCHER |
| GET    | `/api/v1/consumption-measurements/{id}`   | Obter medição por ID                          | RESEARCHER |
| PUT    | `/api/v1/consumption-measurements/{id}`   | Atualizar medição                             | MANAGER    |
| DELETE | `/api/v1/consumption-measurements/{id}`   | Excluir medição                               | MANAGER    |

Parâmetros de filtro em `GET /api/v1/consumption-measurements`:
- `targetType=COMPUTER|MONITOR|COMBINED`
- `equipmentModelId=<uuid>`
- `operatingSystemId=<uuid>`
- `monitorId=<uuid>`
- Paginação: `page`, `size`; ordenação default: `measurementDate DESC`

**Cálculo (extensão do PRD 06)**

| Método | Rota                                              | Descrição                                               |
| ------ | ------------------------------------------------- | ------------------------------------------------------- |
| GET    | `/api/v1/academic-periods/{id}/emissions`         | Cálculo com estratégia LATEST (comportamento existente, atualizado) |
| POST   | `/api/v1/academic-periods/{id}/emissions`         | Cálculo com seleção explícita de medições (novo)        |

### Contratos da API

```json
// POST /api/v1/consumption-measurements
// Request — tipo COMPUTER
{
  "targetType": "COMPUTER",
  "equipmentModelId": "aaa-...",
  "operatingSystemId": "os-...",
  "averageWatts": 58.5,
  "durationMinutes": 12,
  "readingIntervalMinutes": 4,
  "measurementDate": "2025-06-15",
  "conditions": "Executando planilha Excel com 2 abas abertas. Sem monitor conectado ao nobreak.",
  "notes": null
}

// Response 201 — sem outlier
{
  "id": "mmm-...",
  "targetType": "COMPUTER",
  "equipmentModelId": "aaa-...",
  "equipmentModelName": "Dell OptiPlex 7090",
  "operatingSystemId": "os-...",
  "operatingSystemName": "Windows 10",
  "monitorId": null,
  "monitorName": null,
  "averageWatts": 58.5,
  "durationMinutes": 12,
  "readingIntervalMinutes": 4,
  "measurementDate": "2025-06-15",
  "conditions": "Executando planilha Excel...",
  "notes": null,
  "outlierAlert": null,
  "createdAt": "2026-10-04T10:00:00Z"
}

// Response 201 — com outlier (retorna 201 mesmo assim; alerta é informativo)
{
  "id": "nnn-...",
  ...
  "averageWatts": 180.0,
  "outlierAlert": {
    "existingMeasurementCount": 3,
    "existingMeanWatts": 58.5,
    "deviationPercent": 207.7,
    "message": "Esta medição está 207,7% acima da média das 3 medições existentes para este alvo."
  }
}
```

```json
// POST /api/v1/consumption-measurements — tipo MONITOR
{
  "targetType": "MONITOR",
  "monitorId": "bbb-...",
  "averageWatts": 22.0,
  "durationMinutes": 12,
  "readingIntervalMinutes": 4,
  "measurementDate": "2025-06-15",
  "conditions": "Brilho em 80%. Exibindo documento de texto."
}
```

```json
// POST /api/v1/consumption-measurements — tipo COMBINED
{
  "targetType": "COMBINED",
  "equipmentModelId": "aaa-...",
  "operatingSystemId": "os-...",
  "monitorId": "bbb-...",
  "averageWatts": 78.0,
  "durationMinutes": 12,
  "readingIntervalMinutes": 4,
  "measurementDate": "2025-06-15",
  "conditions": "Computador e monitor juntos no nobreak. Executando navegador Chrome."
}
```

```json
// GET /api/v1/consumption-measurements?targetType=COMPUTER&equipmentModelId=aaa-...&page=0&size=10
// Response 200
{
  "items": [
    {
      "id": "mmm-...",
      "targetType": "COMPUTER",
      "equipmentModelId": "aaa-...",
      "equipmentModelName": "Dell OptiPlex 7090",
      "operatingSystemId": "os-...",
      "operatingSystemName": "Windows 10",
      "monitorId": null,
      "monitorName": null,
      "averageWatts": 58.5,
      "durationMinutes": 12,
      "readingIntervalMinutes": 4,
      "measurementDate": "2025-06-15",
      "conditions": "...",
      "notes": null,
      "createdAt": "..."
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

```json
// POST /api/v1/academic-periods/{periodId}/emissions
// Cálculo com seleção explícita de medições
// Request
{
  "measurementStrategy": "EXPLICIT",
  "measurementPreferences": [
    {
      "targetType": "COMPUTER",
      "equipmentModelId": "aaa-...",
      "operatingSystemId": "os-...",
      "measurementId": "mmm-..."
    },
    {
      "targetType": "COMBINED",
      "equipmentModelId": "aaa-...",
      "operatingSystemId": "os-...",
      "monitorId": "bbb-...",
      "measurementId": "nnn-..."
    }
  ]
}

// Quando measurementStrategy = "AVERAGE", measurementPreferences pode ser omitido.
// Quando measurementStrategy = "LATEST" (default), o body pode ser omitido inteiramente.
```

```json
// Trecho atualizado do resultado de emissões — inputs.consumptionSources
{
  "inputs": {
    "consumptionSources": [
      {
        "configurationId": "...",
        "label": "Dell OptiPlex 7090 + Windows 10 + Dell P2422H 24\"",
        "source": "measurement_computer+spec_monitor",
        "computerWatts": 58.5,
        "computerSource": {
          "type": "measurement",
          "measurementId": "mmm-...",
          "measurementDate": "2025-06-15"
        },
        "monitorWatts": 21,
        "monitorSource": {
          "type": "specification"
        },
        "totalWatts": 79.5
      },
      {
        "configurationId": "...",
        "label": "Dell OptiPlex 7090 + Windows 10 + HP E231 (conjunto)",
        "source": "measurement_combined",
        "computerWatts": null,
        "computerSource": null,
        "monitorWatts": null,
        "monitorSource": null,
        "totalWatts": 78.0,
        "jointSource": {
          "type": "measurement",
          "measurementId": "nnn-...",
          "measurementDate": "2025-06-15"
        }
      }
    ]
  }
}
```

### Frontend

**Integração nas páginas de equipamentos:**

| Componente                       | Localização                                | Descrição                                                                  |
| -------------------------------- | ------------------------------------------ | -------------------------------------------------------------------------- |
| Aba "Medições" em modelos        | `/equipment-models` → detalhe/expandido    | Lista medições COMPUTER e COMBINED que referenciam o modelo; botão "Registrar Medição" |
| Aba "Medições" em monitores      | `/monitors` → detalhe/expandido            | Lista medições MONITOR e COMBINED que referenciam o monitor; botão "Registrar Medição" |
| Dialog "Registrar Medição"       | Compartilhado (modelos e monitores)        | Formulário com campos condicionais por tipo, valores sugeridos de protocolo |
| Badge de origem no cálculo       | `EmissionsDashboard` → `TransparencyPanel` | Por configuração: ícone/badge distinguindo medição vs. especificação        |

**Dialog "Registrar Medição" — campos:**

Campos comuns (todos os tipos):
- Tipo de alvo: select (Computador / Monitor / Conjunto) *
- Potência média observada (W) *
- Duração da medição (min) * — valor sugerido: 12
- Intervalo entre leituras (min) — valor sugerido: 4
- Data da medição *
- Condições (textarea) — placeholder: "O que estava executando e o que estava conectado ao wattímetro"
- Observações (textarea, opcional)

Campos condicionais:
- COMPUTER: select de modelo de computador *, select de sistema operacional *
- MONITOR: select de monitor *
- COMBINED: select de modelo *, select de sistema operacional *, select de monitor *

Ao receber resposta com `outlierAlert` → toast de aviso informativo (não bloqueia).

**Schema Zod (novo):**

```typescript
// Usando discriminatedUnion por targetType
const consumptionMeasurementBaseSchema = {
  averageWatts: z.coerce.number().positive('Informe um valor positivo'),
  durationMinutes: z.coerce.number().int().positive('Informe um valor positivo'),
  readingIntervalMinutes: z.coerce.number().int().positive().optional(),
  measurementDate: z.string().min(1, 'Informe a data'),
  conditions: z.string().optional(),
  notes: z.string().optional(),
}

export const consumptionMeasurementSchema = z.discriminatedUnion('targetType', [
  z.object({ targetType: z.literal('COMPUTER'), equipmentModelId: z.string().min(1), operatingSystemId: z.string().min(1), ...consumptionMeasurementBaseSchema }),
  z.object({ targetType: z.literal('MONITOR'), monitorId: z.string().min(1), ...consumptionMeasurementBaseSchema }),
  z.object({ targetType: z.literal('COMBINED'), equipmentModelId: z.string().min(1), operatingSystemId: z.string().min(1), monitorId: z.string().min(1), ...consumptionMeasurementBaseSchema }),
])
```

### Dependências

- **Backend:** Nenhuma nova dependência. Usa Spring Data JPA, Flyway, PostgreSQL (existentes).
- **Frontend:** Nenhuma nova biblioteca. Reutiliza padrões de listagem, dialog e formulário já presentes.

---

## Riscos

| Risco | Impacto | Probabilidade | Mitigação |
| ----- | ------- | ------------- | --------- |
| CHECK constraint de integridade por tipo é verbosa; erros de validação vindos do banco têm mensagem genérica | Médio | Alta | Enforçar a mesma lógica na service layer com exceção legível antes de chegar no banco; o CHECK é rede de segurança |
| Outlier com limiar fixo (50%) pode gerar ruído ou falsos alarmes | Baixo | Média | Mostrar média existente e número de medições no alerta para o gestor ter contexto; só calcular com 2+ medições anteriores |
| Múltiplas medições para o mesmo alvo → usuário confuso sobre qual usar | Médio | Alta | Default claro (mais recente); UI lista medições ordenadas por data com badge de "em uso no cálculo" |
| `ConsumptionResolver` adiciona queries no caminho crítico do cálculo | Médio | Baixa | Buscar todas as medições da instituição em batch único antes do cálculo; nunca N+1 por configuração |
| Medição COMBINED e medições separadas coexistindo → resultado divergente conforme estratégia | Alto | Média | Hierarquia clara documentada; `TransparencyPanel` exibe exatamente qual medição foi usada por configuração |
| Medição referencia equipamento que foi excluído posteriormente | Médio | Baixa | FKs com `ON DELETE RESTRICT` — modelo/monitor em uso por medição não pode ser excluído; mensagem de erro orientando |

---

## Plano de Implementação

| Fase | Tarefa | Descrição | Status |
| ---- | ------ | --------- | ------ |
| **1 — Banco** | Migration V19 — operating_system | Criar tabela `operating_system` com RLS; refatorar `configuration`: extrair distintos → popular tabela → add FK `operating_system_id` → migrar dados → drop coluna `operating_system` | Pendente |
| **1 — Banco** | Migration V20 — consumption_measurement | Criar tabela `consumption_measurement` com RLS, índices e CHECK constraint por tipo de alvo | Pendente |
| **1 — Backend** | Refatorar Configuration | Atualizar entidade `Configuration`, DTOs, services e testes para usar `operatingSystemId` FK em vez de string | Pendente |
| **1 — Backend** | Entidade OperatingSystem + CRUD | Entidade JPA, repository, service com CRUD simples, controller | Pendente |
| **2 — Backend** | Entidade JPA | `ConsumptionMeasurement`: campos, FKs nullable, RLS via mesmo padrão de `EquipmentModel` | Pendente |
| **2 — Backend** | Repository | `ConsumptionMeasurementRepository`: queries por modelo+SO, por monitor, por tipo; suporte a Spring Specification | Pendente |
| **2 — Backend** | DTOs | `CreateConsumptionMeasurementRequest` (com validação por tipo), `ConsumptionMeasurementDTO` (com `outlierAlert` opcional) | Pendente |
| **2 — Backend** | Service — CRUD | `ConsumptionMeasurementService`: criar (com detecção de outlier), listar paginado com filtros, atualizar, excluir | Pendente |
| **2 — Backend** | Service — Resolver | `ConsumptionResolver`: recebe medições em batch, aplica hierarquia por configuração, suporta estratégias LATEST/AVERAGE/EXPLICIT | Pendente |
| **2 — Backend** | Controller | `ConsumptionMeasurementController`: endpoints CRUD + filtros | Pendente |
| **2 — Backend** | Integrar cálculo | Atualizar `EmissionCalculationService` para usar `ConsumptionResolver`; adicionar POST `/emissions`; atualizar DTO `InputConsumption` com campos de fonte por componente | Pendente |
| **2 — Backend** | Exceções | `MeasurementNotFoundException` (404), `InvalidMeasurementTargetException` (400) — registrar no `GlobalExceptionHandler` | Pendente |
| **3 — Frontend** | API client | `lib/api/consumption-measurements.ts`: tipos + funções CRUD | Pendente |
| **3 — Frontend** | Dialog medição | Dialog com campos condicionais por tipo, valores sugeridos, validação Zod, toast de outlier | Pendente |
| **3 — Frontend** | Aba "Medições" em modelos | Lista de medições COMPUTER/COMBINED pelo modelo, botão registrar, ordenação por data | Pendente |
| **3 — Frontend** | Aba "Medições" em monitores | Lista de medições MONITOR/COMBINED pelo monitor, botão registrar | Pendente |
| **3 — Frontend** | Dashboard emissões | Atualizar `TransparencyPanel` para exibir origem por componente (medição vs. especificação) com detalhes da medição usada | Pendente |
| **4 — Testes** | Testes unitários | `ConsumptionResolver` (hierarquia, estratégias), lógica de outlier, validação de tipo de alvo | Pendente |
| **4 — Testes** | Testes de integração | CRUD de medições, RLS, FK constraints, integração com endpoint de cálculo | Pendente |

---

## Estratégia de Testes

| Tipo | Escopo | Abordagem |
| ---- | ------ | --------- |
| **Unitário (backend)** | `ConsumptionResolver`, cálculo de outlier, validação de tipo de alvo | Testes isolados com dados fixos, sem banco |
| **Integração (backend)** | CRUD de medições + integração com cálculo end-to-end | Requisições HTTP com PostgreSQL real (Testcontainers) |

**Cenários críticos a testar:**

**CRUD de medições:**
- Criar medição COMPUTER com campos corretos → 201
- Criar medição MONITOR com campos corretos → 201
- Criar medição COMBINED com `equipmentModelId` + `operatingSystemId` + `monitorId` → 201
- Criar medição COMPUTER sem `equipmentModelId` → 400
- Criar medição COMBINED sem `monitorId` → 400
- Criar medição COMPUTER com `monitorId` preenchido → 400 (campo inválido para o tipo)
- Criar medição com `averageWatts` ≤ 0 → 400
- Criar medição com `durationMinutes` ≤ 0 → 400
- Criar 3ª medição que é outlier (50%+ acima da média das 2 anteriores) → 201 com `outlierAlert` populado
- Criar 1ª e 2ª medição para o mesmo alvo → 201 sem `outlierAlert` (insuficiente para detectar)
- Listar medições com `targetType=COMPUTER&equipmentModelId=X` → retorna só medições COMPUTER do modelo X
- Listar medições com `monitorId=Y` → retorna MONITOR e COMBINED que referenciam Y
- Atualizar medição → 200 com campos atualizados
- Excluir medição existente → 204
- Instituição A não vê medições da instituição B (RLS)
- Tentar excluir modelo/monitor referenciado por medição → 409 Conflict

**ConsumptionResolver (unitário):**
- Sem medições → source = "specification", usa tdpWatts + monitorWatts
- Só medição COMPUTER → source = "measurement_computer+spec_monitor"
- Só medição MONITOR → source = "spec_computer+measurement_monitor"
- Medição COMPUTER + MONITOR → source = "measurement_computer+measurement_monitor"
- Medição COMBINED para (model + OS + monitor) → source = "measurement_combined", totalWatts = medição; computerWatts e monitorWatts null
- Medição COMBINED + COMPUTER separada para o mesmo (model+OS) → COMBINED tem prioridade
- 2 medições COMPUTER com estratégia LATEST → usa a com `measurementDate` mais recente
- 2 medições COMPUTER com estratégia AVERAGE → usa média de `averageWatts`
- 2 medições COMPUTER com estratégia EXPLICIT e ID específico → usa exatamente aquela

**Integração com cálculo:**
- GET emissões sem medições cadastradas → resultado igual ao PRD 06, source = "specification"
- GET emissões com medição COMPUTER → computerWatts da medição, source atualizado
- POST emissões com `measurementStrategy=AVERAGE` → usa média das medições
- POST emissões com `measurementStrategy=EXPLICIT` → usa medição especificada
- Configuração sem medição e com spec → cálculo continua (usa spec)
- Configuração sem medição nem spec (equipment sem tdp e monitor sem watts) → sinalizações de ausência inalteradas

---

## Questões em aberto

| # | Questão | Status |
| - | ------- | ------ |
| 1 | A plataforma deve sugerir o protocolo do estudo (12 min, 4 min de intervalo)? | Resolvida: sim — valores sugeridos na UI, editáveis |
| 2 | Brilho do monitor como campo estruturado? | Resolvida: fora do escopo V1 — documentar em `conditions` |
| 3 | Consumo em estado ocioso ou desligado mas conectado? | Resolvida: fora do escopo — assume uso típico de aula como o estudo |
