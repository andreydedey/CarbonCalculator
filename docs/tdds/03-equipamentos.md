# TDD — Cadastro do Parque Computacional

| Campo            | Valor                                          |
| ---------------- | ---------------------------------------------- |
| Tech Lead        | Andrey Dedey                                   |
| PRD de origem    | `docs/prds/03-equipamentos.md`                 |
| RFC              | `docs/rfcs/001-equipamentos-composicao-vs-modelo-monolitico.md` (Opção 3 aprovada) |
| ADRs relevantes  | ADR-001 (stack), ADR-004 (multi-tenancy RLS)   |
| Status           | Draft                                          |
| Criado em        | 2026-09-21                                     |
| Atualizado em    | 2026-09-22                                     |

---

## Contexto

As entidades de instituição e laboratório já existem e estão funcionais (TDD-02). O próximo elo da cadeia é o parque computacional: saber **quais equipamentos** estão em cada laboratório e em **que quantidade**. Sem essa informação, não existe cálculo de emissão — o consumo de energia depende diretamente do hardware e do sistema operacional em uso.

A RFC 001 decidiu pela **Opção 3**: separar o monitor em entidade própria e mover a composição (computador + SO + monitor + quantidade) para o nível de laboratório. A decisão se fundamenta no limite físico da medição — um wattímetro de tomada mede o computador inteiro ou o monitor, nunca componentes internos — e na eliminação da duplicação mais frequente nos dados de referência (dois modelos de computador compartilhando o mesmo monitor).

### O que existe hoje

O branch `feat/equipamentos` contém uma implementação monolítica do PRD 03 original: `EquipmentModel` com todos os campos (CPU, RAM, monitor, SO, descrição) numa única entidade, vinculado a laboratórios via `LaboratoryEquipment` (modelo + SO + quantidade). Essa implementação precisa ser **refatorada**, não descartada.

### Decisões resolvidas

- **Monitor como entidade separada.** O monitor se repete entre computadores diferentes (no estudo FACOMP, HP 1 e HP 2 usam o mesmo HP E231). Cadastrar uma vez e reutilizar elimina duplicação e permite medições independentes.
- **SO na composição do laboratório, não no modelo.** O mesmo Dell OptiPlex pode rodar Windows 11 no Lab A e Ubuntu no Lab B. O SO afeta o consumo (~2x entre Windows 10 e 11) e é a variável que a simulação de cenários mais vai manipular.
- **GPU como campos opcionais no modelo de computador.** GPU dedicada é parte do gabinete e medida junto pelo wattímetro. Dois campos opcionais (`gpuModel`, `gpuTdpWatts`) bastam — nulos quando não houver placa dedicada.
- **Flag `hasIntegratedScreen` no modelo de computador.** Notebooks e all-in-ones com tela integrada não precisam de monitor externo na composição. A flag dispensa o alerta de monitor ausente.
- **Ano de fabricação e vida útil não entram agora.** Pertencem ao escopo 3, ainda não definido. Se forem necessários depois, será uma migration `ALTER TABLE` sem quebra.

## Definição do Problema

O sistema tem instituições e laboratórios, mas não sabe o que está dentro deles. Sem o cadastro do parque:

- Não é possível calcular emissões (PRD 06 depende diretamente)
- Não é possível registrar medições de consumo (PRD 04 precisa saber a qual modelo/monitor vincular)
- Não é possível simular cenários de troca de OS, monitor ou renovação de hardware (PRD 07)

**O que acontece se não resolvermos:**
- Toda a cadeia posterior (medições → calendário → cálculo → simulação) fica bloqueada.

## Escopo

### Dentro do escopo

- Entidade `EquipmentModel` (modelo de computador, escopo da instituição) — refatorada: sem monitor, sem SO, com GPU opcional e flag de tela integrada
- Entidade `Monitor` (nova — modelo de monitor, escopo da instituição)
- Entidade `LaboratoryEquipment` (composição: computador + SO + monitor + quantidade) — refatorada: com FK para monitor
- CRUD de modelos de computador
- CRUD de monitores
- Composição de laboratórios: vincular configurações (computador + SO + monitor + quantidade)
- Alerta visual quando monitor ausente em computador sem tela integrada
- Reutilização de modelo/monitor existente em outro laboratório
- Validação de quantidade > 0
- Visualização da composição do laboratório (configurações, quantidades, total)
- Listagem paginada de modelos de computador com busca por nome
- Listagem paginada de monitores com busca por nome
- Bloqueio de exclusão de modelo/monitor em uso
- Migration de dados: migrar monitores embutidos no `equipment_model` para a nova tabela `monitor`

### Fora do escopo

- Cadastro individual de máquinas (número de série, patrimônio)
- Descoberta automática de hardware por agente
- Catálogo compartilhado entre instituições
- Consumo medido por componente interno do computador (processador, memória, placa de vídeo)
- Periféricos além do monitor (teclado, mouse, caixas de som, webcams)
- Dados de fabricação, ciclo de vida ou descarte (escopo 3)
- Equipamentos que não sejam estações de trabalho e monitores
- Versionamento temporal das configurações (a alteração vale a partir do próximo cálculo; resultados anteriores são imutáveis por design)

---

## Solução Técnica

### Visão geral da arquitetura

```
┌─────────────────────────────────────────────────────────┐
│  Frontend (React + Vite + shadcn/ui)                    │
│                                                         │
│  Computadores: CRUD + listagem paginada com busca       │
│  Monitores: CRUD + listagem paginada com busca          │
│  Lab → Composição: computador + SO + monitor + qty      │
│  Alerta de monitor ausente (se não tem tela integrada)  │
│                                                         │
│  Zod schemas  ──→  React Hook Form  ──→  formulários    │
└──────────────────────────┬──────────────────────────────┘
                           │ HTTP (JSON)
                           │ Header: X-Institution-Id
┌──────────────────────────▼──────────────────────────────┐
│  Backend (Spring Boot)                                  │
│                                                         │
│  EquipmentModelController  ──→  EquipmentModelService   │
│  MonitorController  ──→  MonitorService                 │
│  LaboratoryEquipmentController ──→ LabEquipmentService  │
│                                                         │
│  Validação na service layer (quantidade, campos, GPU)   │
└──────────────────────────┬──────────────────────────────┘
                           │ JDBC
                           │ SET app.current_institution
┌──────────────────────────▼──────────────────────────────┐
│  PostgreSQL + RLS                                       │
│  equipment_model (RLS por institution_id)               │
│  monitor (RLS por institution_id)                       │
│  laboratory_equipment (sem RLS — lab já é filtrado)     │
└─────────────────────────────────────────────────────────┘
```

### Modelo de dados

**Tabela `equipment_model`** (modelo de computador — escopo da instituição)

| Coluna                 | Tipo                 | Restrições                      | Status     |
| ---------------------- | -------------------- | ------------------------------- | ---------- |
| `id`                   | `UUID`               | PK, gerado automaticamente     | Existente  |
| `institution_id`       | `UUID`               | FK → institution(id), NOT NULL  | Existente  |
| `name`                 | `VARCHAR(255)`       | NOT NULL                        | Existente  |
| `equipment_type`       | `VARCHAR(50)`        | NULL                            | Existente  |
| `processor`            | `VARCHAR(255)`       | NULL                            | Existente  |
| `tdp_watts`            | `INTEGER`            | NULL                            | Existente  |
| `core_count`           | `INTEGER`            | NULL                            | Existente  |
| `memory_gb`            | `INTEGER`            | NULL                            | Existente  |
| `gpu_model`            | `VARCHAR(255)`       | NULL                            | **Novo**   |
| `gpu_tdp_watts`        | `INTEGER`            | NULL                            | **Novo**   |
| `has_integrated_screen`| `BOOLEAN`            | NOT NULL, DEFAULT FALSE         | **Novo**   |
| `description`          | `TEXT`               | NULL                            | Existente  |
| `created_at`           | `TIMESTAMP WITH TZ`  | NOT NULL                        | Existente  |
| `updated_at`           | `TIMESTAMP WITH TZ`  | NOT NULL                        | Existente  |

**Colunas removidas:** `monitor_name`, `monitor_watts`, `operating_system`

**RLS:** Mantido — mesma policy existente.

**Tabela `monitor`** (modelo de monitor — escopo da instituição) — **Nova**

| Coluna           | Tipo                 | Restrições                      |
| ---------------- | -------------------- | ------------------------------- |
| `id`             | `UUID`               | PK, gerado automaticamente     |
| `institution_id` | `UUID`               | FK → institution(id), NOT NULL  |
| `name`           | `VARCHAR(255)`       | NOT NULL                        |
| `watts`          | `INTEGER`            | NULL (potência nominal, pode ser desconhecida) |
| `created_at`     | `TIMESTAMP WITH TZ`  | NOT NULL                        |
| `updated_at`     | `TIMESTAMP WITH TZ`  | NOT NULL                        |

**RLS:** Habilitado com policy filtrando por `current_setting('app.current_institution')`. Mesmo padrão de `equipment_model`.

**Tabela `laboratory_equipment`** (composição: computador + SO + monitor + quantidade)

| Coluna               | Tipo               | Restrições                               | Status     |
| -------------------- | ------------------ | ---------------------------------------- | ---------- |
| `id`                 | `UUID`             | PK, gerado automaticamente              | Existente  |
| `laboratory_id`      | `UUID`             | FK → laboratory(id), NOT NULL            | Existente  |
| `equipment_model_id` | `UUID`             | FK → equipment_model(id), NOT NULL       | Existente  |
| `operating_system`   | `VARCHAR(100)`     | NOT NULL                                 | Existente  |
| `monitor_id`         | `UUID`             | FK → monitor(id), NULL                   | **Novo**   |
| `quantity`           | `INTEGER`          | NOT NULL, CHECK (quantity > 0)           | Existente  |
| `created_at`         | `TIMESTAMP WITH TZ`| NOT NULL                                | Existente  |
| `updated_at`         | `TIMESTAMP WITH TZ`| NOT NULL                                | Existente  |

**Constraints atualizadas:**
- `UNIQUE(laboratory_id, equipment_model_id, operating_system, monitor_id)` — substitui a atual `(laboratory_id, equipment_model_id, operating_system)`. Permite o mesmo computador com mesmo SO mas monitores diferentes.
- `monitor_id` é nullable: nulo quando `equipment_model.has_integrated_screen = true` ou quando o gestor confirma ausência de monitor.
- FK `monitor_id` com `ON DELETE RESTRICT` — monitor em uso não pode ser excluído.

**Índices:**
- `equipment_model(institution_id)` — RLS e listagem (existente)
- `monitor(institution_id)` — RLS e listagem (novo)
- `laboratory_equipment(laboratory_id)` — composição do lab (existente)
- `laboratory_equipment(equipment_model_id)` — verificar dependentes (existente)
- `laboratory_equipment(monitor_id)` — verificar dependentes (novo)

### Decisões de modelagem

- **SO na composição, não no modelo.** O mesmo Dell OptiPlex pode rodar Windows 11 no Lab A e Ubuntu no Lab B. O SO determina o consumo e é o dado que a simulação de cenários mais vai manipular. Colocá-lo no modelo forçaria duplicação de hardware idêntico.
- **Monitor como FK na composição, não no modelo.** O monitor se repete entre computadores. Na composição, o gestor seleciona computador + SO + monitor, formando uma configuração completa. Monitores medem-se isoladamente e a medição é reutilizável.
- **`monitor_id` nullable.** Computadores com tela integrada (notebooks, all-in-ones) não precisam de monitor externo. Computadores sem tela integrada também podem ficar sem monitor cadastrado, mas com alerta de subestimativa.
- **GPU como campos no modelo, não entidade separada.** GPU dedicada é parte do gabinete e medida junto pelo wattímetro. Dois campos opcionais bastam. Se `gpuModel` for informado, `gpuTdpWatts` deve ser obrigatório (validado na service layer).
- **Unique constraint inclui `monitor_id`.** Permite a mesma máquina com mesmo SO mas monitores diferentes no mesmo lab (caso raro, mas correto). `NULL` em `monitor_id` é tratado como valor distinto pelo PostgreSQL com `NULLS NOT DISTINCT` ou via partial index.
- **Quantidade validada no backend.** O `CHECK (quantity > 0)` no banco garante integridade; a service layer dá a mensagem amigável.
- **Sem soft-delete de modelos ou monitores.** Podem ser excluídos quando não estão em uso. Se vinculados, a exclusão é bloqueada com mensagem orientando a desassociação.

### Migration de dados

A refatoração parte da implementação existente no branch `feat/equipamentos`. A migration deve ser executável tanto sobre bancos novos (que já têm V10 e V11) quanto sobre bancos com dados existentes.

**V12 — Reestruturação para modelo composto:**

```sql
-- 1. Criar tabela monitor
CREATE TABLE monitor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institution (id),
    name VARCHAR(255) NOT NULL,
    watts INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_monitor_institution_id ON monitor (institution_id);
ALTER TABLE monitor ENABLE ROW LEVEL SECURITY;
ALTER TABLE monitor FORCE ROW LEVEL SECURITY;
CREATE POLICY monitor_institution_isolation ON monitor
    USING (institution_id = current_setting('app.current_institution', true)::uuid);

-- 2. Migrar monitores existentes do equipment_model para a tabela monitor
INSERT INTO monitor (institution_id, name, watts)
SELECT DISTINCT institution_id, monitor_name, monitor_watts
FROM equipment_model
WHERE monitor_name IS NOT NULL;

-- 3. Adicionar colunas novas ao equipment_model
ALTER TABLE equipment_model
    ADD COLUMN gpu_model VARCHAR(255),
    ADD COLUMN gpu_tdp_watts INTEGER,
    ADD COLUMN has_integrated_screen BOOLEAN NOT NULL DEFAULT FALSE;

-- 4. Adicionar FK monitor_id ao laboratory_equipment
ALTER TABLE laboratory_equipment
    ADD COLUMN monitor_id UUID REFERENCES monitor (id) ON DELETE RESTRICT;
CREATE INDEX idx_laboratory_equipment_monitor_id ON laboratory_equipment (monitor_id);

-- 5. Preencher monitor_id nos registros existentes
UPDATE laboratory_equipment le
SET monitor_id = m.id
FROM equipment_model em
JOIN monitor m ON m.institution_id = em.institution_id
    AND m.name = em.monitor_name
    AND (m.watts = em.monitor_watts OR (m.watts IS NULL AND em.monitor_watts IS NULL))
WHERE le.equipment_model_id = em.id
    AND em.monitor_name IS NOT NULL;

-- 6. Remover colunas migradas do equipment_model
ALTER TABLE equipment_model
    DROP COLUMN monitor_name,
    DROP COLUMN monitor_watts,
    DROP COLUMN operating_system;

-- 7. Atualizar unique constraint do laboratory_equipment
ALTER TABLE laboratory_equipment
    DROP CONSTRAINT laboratory_equipment_laboratory_id_equipment_model_id_oper_key;
ALTER TABLE laboratory_equipment
    ADD CONSTRAINT uq_lab_equipment_config
    UNIQUE NULLS NOT DISTINCT (laboratory_id, equipment_model_id, operating_system, monitor_id);
```

**Observação:** Se `NULLS NOT DISTINCT` não for suportado na versão do PostgreSQL em uso (< 15), usar um partial unique index:

```sql
-- Alternativa para PostgreSQL < 15
CREATE UNIQUE INDEX uq_lab_equipment_with_monitor
    ON laboratory_equipment (laboratory_id, equipment_model_id, operating_system, monitor_id)
    WHERE monitor_id IS NOT NULL;
CREATE UNIQUE INDEX uq_lab_equipment_without_monitor
    ON laboratory_equipment (laboratory_id, equipment_model_id, operating_system)
    WHERE monitor_id IS NULL;
```

### API REST

**Modelos de Computador** (filtrados por RLS via header `X-Institution-Id`)

| Método | Rota                            | Descrição                    | Status | Permissão  |
| ------ | ------------------------------- | ---------------------------- | ------ | ---------- |
| POST   | `/api/v1/equipment-models`      | Criar modelo                 | 201    | MANAGER    |
| GET    | `/api/v1/equipment-models`      | Listar modelos (paginado)    | 200    | RESEARCHER |
| GET    | `/api/v1/equipment-models/{id}` | Obter modelo por ID          | 200    | RESEARCHER |
| PUT    | `/api/v1/equipment-models/{id}` | Atualizar modelo             | 200    | MANAGER    |
| DELETE | `/api/v1/equipment-models/{id}` | Excluir (sem vínculos)       | 204    | MANAGER    |

**Monitores** (filtrados por RLS via header `X-Institution-Id`)

| Método | Rota                       | Descrição                    | Status | Permissão  |
| ------ | -------------------------- | ---------------------------- | ------ | ---------- |
| POST   | `/api/v1/monitors`         | Criar monitor                | 201    | MANAGER    |
| GET    | `/api/v1/monitors`         | Listar monitores (paginado)  | 200    | RESEARCHER |
| GET    | `/api/v1/monitors/{id}`    | Obter monitor por ID         | 200    | RESEARCHER |
| PUT    | `/api/v1/monitors/{id}`    | Atualizar monitor            | 200    | MANAGER    |
| DELETE | `/api/v1/monitors/{id}`    | Excluir (sem vínculos)       | 204    | MANAGER    |

**Composição do Laboratório** (aninhado sob laboratório)

| Método | Rota                                                  | Descrição                                               | Status | Permissão  |
| ------ | ----------------------------------------------------- | ------------------------------------------------------- | ------ | ---------- |
| POST   | `/api/v1/laboratories/{labId}/equipment`               | Vincular configuração (computador + SO + monitor + qty) | 201    | MANAGER    |
| GET    | `/api/v1/laboratories/{labId}/equipment`               | Listar composição do lab                                | 200    | RESEARCHER |
| PUT    | `/api/v1/laboratories/{labId}/equipment/{id}`          | Atualizar (quantidade, SO, monitor)                     | 200    | MANAGER    |
| DELETE | `/api/v1/laboratories/{labId}/equipment/{id}`          | Desvincular configuração                                | 204    | MANAGER    |

**Contratos principais:**

```json
// POST /api/v1/equipment-models
// Header: X-Institution-Id: 550e8400-...
// Request
{
  "name": "HP ProDesk 400 G6",
  "equipmentType": "Desktop",
  "processor": "Intel Core i7-8700",
  "tdpWatts": 65,
  "coreCount": 6,
  "memoryGb": 8,
  "gpuModel": null,
  "gpuTdpWatts": null,
  "hasIntegratedScreen": false,
  "description": null
}

// Response 201
{
  "id": "aaa-...",
  "name": "HP ProDesk 400 G6",
  "equipmentType": "Desktop",
  "processor": "Intel Core i7-8700",
  "tdpWatts": 65,
  "coreCount": 6,
  "memoryGb": 8,
  "gpuModel": null,
  "gpuTdpWatts": null,
  "hasIntegratedScreen": false,
  "description": null,
  "createdAt": "2026-09-22T10:00:00Z"
}
```

```json
// POST /api/v1/monitors
// Header: X-Institution-Id: 550e8400-...
// Request
{
  "name": "HP E231",
  "watts": 25
}

// Response 201
{
  "id": "mmm-...",
  "name": "HP E231",
  "watts": 25,
  "createdAt": "2026-09-22T10:00:00Z"
}
```

```json
// POST /api/v1/laboratories/{labId}/equipment
// Header: X-Institution-Id: 550e8400-...
// Request
{
  "equipmentModelId": "aaa-...",
  "operatingSystem": "Windows 11",
  "monitorId": "mmm-...",
  "quantity": 30
}

// Response 201
{
  "id": "bbb-...",
  "equipmentModel": {
    "id": "aaa-...",
    "name": "HP ProDesk 400 G6",
    "processor": "Intel Core i7-8700",
    "tdpWatts": 65,
    "coreCount": 6,
    "memoryGb": 8,
    "hasIntegratedScreen": false
  },
  "operatingSystem": "Windows 11",
  "monitor": {
    "id": "mmm-...",
    "name": "HP E231",
    "watts": 25
  },
  "quantity": 30,
  "createdAt": "2026-09-22T10:00:00Z"
}
```

```json
// GET /api/v1/laboratories/{labId}/equipment
// Header: X-Institution-Id: 550e8400-...
// Response 200
{
  "items": [
    {
      "id": "bbb-...",
      "equipmentModel": {
        "id": "aaa-...",
        "name": "HP ProDesk 400 G6",
        "processor": "Intel Core i7-8700",
        "tdpWatts": 65,
        "coreCount": 6,
        "memoryGb": 8,
        "hasIntegratedScreen": false
      },
      "operatingSystem": "Windows 11",
      "monitor": {
        "id": "mmm-...",
        "name": "HP E231",
        "watts": 25
      },
      "quantity": 30
    },
    {
      "id": "ccc-...",
      "equipmentModel": {
        "id": "ddd-...",
        "name": "Dell OptiPlex 3070",
        "processor": "Intel Core i5-9500",
        "tdpWatts": 65,
        "coreCount": 6,
        "memoryGb": 4,
        "hasIntegratedScreen": false
      },
      "operatingSystem": "Ubuntu 22.04 LTS",
      "monitor": null,
      "quantity": 15
    }
  ],
  "totalMachines": 45,
  "configurationsWithoutMonitor": 1
}
```

**Regras de negócio na API:**

- `DELETE /equipment-models/{id}` retorna `409 Conflict` se o modelo está vinculado a algum laboratório, com mensagem orientando a desassociação
- `DELETE /monitors/{id}` retorna `409 Conflict` se o monitor está vinculado a alguma configuração
- `POST /equipment-models` com `gpuModel` preenchido mas `gpuTdpWatts` nulo retorna `400 Bad Request`
- `POST /laboratories/{labId}/equipment` com `quantity <= 0` retorna `400 Bad Request`
- `POST /laboratories/{labId}/equipment` com combinação duplicada (mesmo modelo + SO + monitor no mesmo lab) retorna `409 Conflict`
- `POST /laboratories/{labId}/equipment` sem `monitorId` quando o modelo não tem tela integrada: aceita, mas a resposta sinaliza ausência de monitor (campo `monitor: null`). O alerta de subestimativa é responsabilidade do frontend.
- A resposta de composição inclui `totalMachines` (soma das quantidades) e `configurationsWithoutMonitor` (count de configurações sem monitor em modelos sem tela integrada)
- `GET /equipment-models` aceita `?name=` para busca por nome (like, case-insensitive) e paginação padrão (`page`, `size`, sort por `name` ASC)
- `GET /monitors` aceita `?name=` para busca por nome e paginação padrão

### Backend — Entidades

**`EquipmentModel`** (refatorada)

Campos removidos: `monitorName`, `monitorWatts`, `operatingSystem`
Campos adicionados: `gpuModel`, `gpuTdpWatts`, `hasIntegratedScreen`
Método `hasMonitor()` removido (não faz mais sentido — monitor é entidade separada).

**`Monitor`** (nova)

Entidade simples com `institution` (ManyToOne), `name`, `watts`, timestamps. Segue o mesmo padrão de `EquipmentModel` com `@PrePersist`/`@PreUpdate`.

**`LaboratoryEquipment`** (refatorada)

Campo adicionado: `monitor` (ManyToOne, LAZY, nullable, FK para `Monitor`).

### Backend — DTOs

**Modelos de computador:**

| DTO                         | Campos                                                                                                   |
| --------------------------- | -------------------------------------------------------------------------------------------------------- |
| `CreateEquipmentModelRequest` | `name`, `equipmentType`, `processor`, `tdpWatts`, `coreCount`, `memoryGb`, `gpuModel`, `gpuTdpWatts`, `hasIntegratedScreen`, `description` |
| `EquipmentModelDTO`          | Todos acima + `id`, `createdAt`                                                                          |

**Monitores:**

| DTO                    | Campos                     |
| ---------------------- | -------------------------- |
| `CreateMonitorRequest` | `name`, `watts`            |
| `MonitorDTO`           | `id`, `name`, `watts`, `createdAt` |

**Composição do laboratório:**

| DTO                                | Campos                                                                                               |
| ---------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `CreateLaboratoryEquipmentRequest` | `equipmentModelId`, `operatingSystem`, `monitorId` (nullable), `quantity`                            |
| `LaboratoryEquipmentDTO`          | `id`, `equipmentModel` (EquipmentModelSummaryDTO), `operatingSystem`, `monitor` (MonitorDTO nullable), `quantity`, `createdAt` |
| `EquipmentModelSummaryDTO`         | `id`, `name`, `processor`, `tdpWatts`, `coreCount`, `memoryGb`, `hasIntegratedScreen`                |
| `LaboratoryCompositionDTO`         | `items` (List), `totalMachines`, `configurationsWithoutMonitor`                                      |

### Backend — Services

**`MonitorService`** (novo)

Mesmo padrão de `EquipmentModelService`:
- `create(CreateMonitorRequest)` — valida `name` não vazio, seta `institution` via `TenantContext`
- `getById(UUID id)` — retorna ou lança `MonitorNotFoundException`
- `update(UUID id, CreateMonitorRequest)` — atualiza campos
- `list(String name, Pageable pageable)` — busca paginada com filtro opcional por nome
- `delete(UUID id)` — verifica dependentes via `LaboratoryEquipmentRepository.existsByMonitorId()`, lança `MonitorHasDependentsException` se em uso

**`EquipmentModelService`** (refatorado)

- Mapeamento atualizado para os novos campos (GPU, `hasIntegratedScreen`)
- Campos de monitor removidos do mapeamento
- Validação: se `gpuModel` informado, `gpuTdpWatts` é obrigatório

**`LaboratoryEquipmentService`** (refatorado)

- `create` aceita `monitorId` opcional, resolve via `MonitorService.getOrThrow()`
- Verificação de duplicata atualizada para incluir `monitorId` na chave
- `getComposition` calcula `configurationsWithoutMonitor`: conta configurações onde `monitor` é nulo e o modelo não tem tela integrada

### Backend — Exceções

**Novas:**
- `MonitorNotFoundException` → 404
- `MonitorHasDependentsException` → 409
- `MissingMonitorNameException` → 400
- `GpuTdpRequiredException` → 400 (quando `gpuModel` informado sem `gpuTdpWatts`)

**Registrar em `GlobalExceptionHandler`:**
- 400: `MissingMonitorNameException`, `GpuTdpRequiredException`
- 404: `MonitorNotFoundException`
- 409: `MonitorHasDependentsException`

### Backend — Repositories

**`MonitorRepository`** (novo)

Estende `JpaRepository<Monitor, UUID>` e `JpaSpecificationExecutor<Monitor>`.

**`LaboratoryEquipmentRepository`** (atualizado)

Novo método: `boolean existsByMonitorId(UUID monitorId)` — para bloquear exclusão de monitor em uso.
Método de duplicata atualizado: `existsByLaboratoryIdAndEquipmentModelIdAndOperatingSystemAndMonitorId(...)` — ou substituído por query JPQL customizada para tratar `NULL` em `monitorId`.

### Frontend

**Páginas e componentes:**

| Componente                        | Localização                      | Descrição                                                     |
| --------------------------------- | -------------------------------- | ------------------------------------------------------------- |
| Página de modelos de computador   | `/equipment-models`              | Listagem paginada + busca + botão "Novo Computador"           |
| Formulário de modelo              | Dialog                           | Campos do computador (sem monitor, sem SO) + seção GPU        |
| Página de monitores               | `/monitors`                      | Listagem paginada + busca + botão "Novo Monitor"              |
| Formulário de monitor             | Dialog                           | Nome + potência nominal (watts)                               |
| Seção composição no lab           | Dentro de `LaboratoryForm`       | Tabela de configurações com computador, SO, monitor, qtd      |
| Dialog "Vincular Configuração"    | Dialog                           | Select de computador + input SO + select de monitor + qty     |

**Formulário de modelo de computador (refatorado):**

Campos removidos: monitor (nome, watts), sistema operacional.
Campos adicionados: GPU modelo, GPU TDP (watts), tela integrada (checkbox).

Layout:
- **Linha 1**: Modelo do Equipamento * + Tipo (select)
- **Linha 2**: Processador (CPU) + TDP (Watts) + Núcleos
- **Linha 3**: Memória RAM (select) + GPU Modelo + GPU TDP (Watts)
- **Linha 4**: Tela integrada (checkbox) + Descrição (opcional)

Quando "Tela integrada" está marcado, os campos de GPU devem estar disponíveis (notebooks gamer podem ter GPU dedicada).

**Formulário de monitor (novo):**

Layout simples:
- **Linha 1**: Modelo do Monitor * + Potência nominal (Watts)

**Dialog "Vincular Configuração" (refatorado):**

- Select de modelo de computador (com busca, carrega até 100)
- Input de sistema operacional (select com opções do `OS_OPTIONS`)
- Select de monitor (com busca, carrega até 100) — **desabilitado e oculto se o modelo selecionado tem tela integrada**
- Input de quantidade (number, min 1)
- Alerta se monitor não selecionado e modelo não tem tela integrada: "Sem dados de monitor, o consumo calculado ficará subestimado."

**Composição do laboratório (tabela refatorada):**

| Coluna             | Conteúdo                                           |
| ------------------ | -------------------------------------------------- |
| Modelo             | Nome do modelo de computador                       |
| CPU / TDP          | Processador + watts (ex: "Intel i7-8700 / 65W")   |
| Núcleos            | Número de núcleos                                  |
| RAM                | Memória em GB                                      |
| Monitor            | Nome do monitor + watts, ou badge "Sem monitor"    |
| Sistema Op.        | SO da configuração                                 |
| Qtd.               | Quantidade de estações                             |
| Ações              | Editar / Desvincular                               |

Rodapé: "Total: X máquinas" + alerta se alguma configuração não tem monitor (em modelo sem tela integrada).

**Schemas Zod (atualizados):**

```typescript
// equipmentModelSchema.ts
export const equipmentModelFormSchema = z.object({
  name: z.string().trim().min(1, 'Informe o nome do modelo'),
  equipmentType: z.string().optional().default(''),
  processor: z.string().trim().optional().default(''),
  tdpWatts: z.coerce.number().int().positive('Informe um valor positivo').optional(),
  coreCount: z.coerce.number().int().positive('Informe um valor positivo').optional(),
  memoryGb: z.coerce.number().int().positive('Informe um valor positivo').optional(),
  gpuModel: z.string().trim().optional().default(''),
  gpuTdpWatts: z.coerce.number().int().positive('Informe um valor positivo').optional(),
  hasIntegratedScreen: z.boolean().default(false),
  description: z.string().trim().optional().default(''),
}).refine(
  (data) => !data.gpuModel || data.gpuTdpWatts,
  { message: 'Informe o TDP da GPU', path: ['gpuTdpWatts'] }
)

// monitorSchema.ts (novo)
export const monitorFormSchema = z.object({
  name: z.string().trim().min(1, 'Informe o nome do monitor'),
  watts: z.coerce.number().int().positive('Informe um valor positivo').optional(),
})

// laboratoryEquipmentSchema.ts (atualizado)
export const laboratoryEquipmentFormSchema = z.object({
  equipmentModelId: z.string().min(1, 'Selecione um modelo de computador'),
  operatingSystem: z.string().trim().min(1, 'Informe o sistema operacional'),
  monitorId: z.string().optional().default(''),
  quantity: z.coerce.number().int().min(1, 'A quantidade deve ser pelo menos 1'),
})
```

**Navegação:**

Sidebar: dois itens no grupo de equipamentos:
- "Computadores" → `/equipment-models` (ícone: Cpu)
- "Monitores" → `/monitors` (ícone: Monitor)

---

## Riscos

| Risco | Impacto | Probabilidade | Mitigação |
|-------|---------|---------------|-----------|
| Migration V12 falha em bancos com dados existentes | Alto | Média | Testar migration com dados de seed no Testcontainers antes de aplicar; passos são idempotentes e em ordem segura |
| Unique constraint com `NULLS NOT DISTINCT` requer PostgreSQL 15+ | Médio | Baixa | Verificar versão do PostgreSQL em uso; se < 15, usar partial unique indexes como alternativa documentada |
| Gestor acha o fluxo de cadastro separado (computador + monitor + composição) confuso | Médio | Média | UI guia o fluxo: ao vincular configuração, se não há monitores cadastrados, oferecer link para cadastrar um |
| Exclusão de modelo/monitor vinculado confusa para o usuário | Médio | Baixa | Mensagem de erro clara indicando quais laboratórios usam o modelo/monitor |
| Dados de monitor opcionais levam a subestimativa silenciosa | Alto | Alta | Alerta visual no frontend + campo `configurationsWithoutMonitor` na API |

---

## Plano de Implementação

A refatoração parte da implementação existente. As fases seguem a mesma estrutura do TDD-02.

| Fase | Tarefa | Descrição | Estimativa |
|------|--------|-----------|------------|
| **1 — Banco** | Migration V12 | Criar tabela `monitor`, adicionar colunas novas, migrar dados, remover colunas antigas, atualizar constraints | 0.5d |
| **2 — Backend** | Entidade `Monitor` + Repository | JPA entity, repository com `JpaSpecificationExecutor` | 0.25d |
| **2 — Backend** | Refatorar `EquipmentModel` | Remover campos de monitor e SO, adicionar GPU e `hasIntegratedScreen` | 0.25d |
| **2 — Backend** | Refatorar `LaboratoryEquipment` | Adicionar relação `monitor` (ManyToOne) | 0.25d |
| **2 — Backend** | DTOs + Mappers | Novos DTOs de monitor, atualizar DTOs de modelo e composição | 0.5d |
| **2 — Backend** | `MonitorService` + Controller | CRUD de monitores, busca paginada, bloqueio de exclusão | 0.5d |
| **2 — Backend** | Refatorar `EquipmentModelService` | Atualizar mapeamento, adicionar validação de GPU | 0.25d |
| **2 — Backend** | Refatorar `LaboratoryEquipmentService` | Aceitar `monitorId`, atualizar verificação de duplicata, atualizar cálculo de composição | 0.5d |
| **2 — Backend** | Exceções | `MonitorNotFoundException`, `MonitorHasDependentsException`, `MissingMonitorNameException`, `GpuTdpRequiredException` + registrar no handler | 0.25d |
| **3 — Frontend** | API client de monitores | `lib/api/monitors.ts` — tipos + funções CRUD | 0.25d |
| **3 — Frontend** | Refatorar API client de equipamentos | Remover campos de monitor/SO, adicionar GPU e `hasIntegratedScreen` | 0.25d |
| **3 — Frontend** | Refatorar API client de lab equipment | Adicionar `monitorId` no payload e `monitor` na resposta | 0.25d |
| **3 — Frontend** | Schemas Zod | Atualizar `equipmentModelSchema`, criar `monitorSchema`, atualizar `laboratoryEquipmentSchema` | 0.25d |
| **3 — Frontend** | Página de monitores | Listagem paginada + busca + dialog CRUD (reusar padrões de `EquipmentModelList`) | 0.5d |
| **3 — Frontend** | Refatorar formulário de modelo | Remover campos de monitor/SO, adicionar GPU e checkbox tela integrada | 0.5d |
| **3 — Frontend** | Refatorar card de modelo | Remover info de monitor, adicionar info de GPU quando presente | 0.25d |
| **3 — Frontend** | Refatorar `LinkEquipmentDialog` | Adicionar select de monitor, lógica de tela integrada, alerta de monitor ausente | 0.5d |
| **3 — Frontend** | Refatorar `LaboratoryEquipmentSection` | Coluna de monitor mostra nome do monitor ou badge, atualizar cálculo | 0.25d |
| **3 — Frontend** | Navegação | Adicionar "Monitores" na sidebar, atualizar rota | 0.25d |
| **4 — Testes** | Testes de integração | Endpoints de monitor + refatorados de modelo e composição + RLS + migration | 1d |
| **4 — Testes** | Testes unitários | Services: validações de GPU, bloqueio de exclusão de monitor, composição com monitor | 0.5d |

**Estimativa total**: ~7.25 dias úteis

**Dependências entre fases:**
- Fase 1 desbloqueia Fase 2
- Fase 2 desbloqueia Fases 3 e 4
- Fases 3 e 4 podem rodar em paralelo

---

## Estratégia de Testes

| Tipo | Escopo | Abordagem |
|------|--------|-----------|
| **Integração (backend)** | Endpoints REST + RLS + constraints + migration | Requisições HTTP reais contra PostgreSQL em container (Testcontainers) |
| **Unitário (backend)** | Services | Mock dos repositories para testar regras de negócio isoladamente |

**Cenários críticos a testar:**

**Monitores:**
- Criar monitor com nome e watts → 201
- Criar monitor sem watts (potência desconhecida) → 201
- Criar monitor sem nome → 400
- Listar monitores com busca por nome → retorna apenas matches
- Atualizar monitor existente → 200
- Excluir monitor sem vínculos → 204
- Excluir monitor vinculado a configuração de laboratório → 409 Conflict
- `GET /monitors` com tenant A não retorna monitores do tenant B (RLS)

**Modelos de computador (refatorados):**
- Criar modelo sem campos de monitor → 201 (campos de monitor não existem mais)
- Criar modelo com GPU (gpuModel + gpuTdpWatts) → 201
- Criar modelo com gpuModel mas sem gpuTdpWatts → 400
- Criar modelo com hasIntegratedScreen = true → 201
- Excluir modelo sem vínculos → 204
- Excluir modelo vinculado a laboratório → 409 Conflict

**Composição do laboratório (refatorada):**
- Vincular configuração com computador + SO + monitor + quantidade → 201
- Vincular configuração sem monitor (monitorId nulo) → 201
- Vincular com quantidade 0 → 400
- Vincular combinação duplicada (mesmo computador + SO + monitor no mesmo lab) → 409
- Vincular mesmo computador com SO diferente no mesmo lab → 201
- Vincular mesmo computador com mesmo SO mas monitor diferente no mesmo lab → 201
- Listar composição → resposta com items, totalMachines, configurationsWithoutMonitor
- `configurationsWithoutMonitor` não conta configurações cujo modelo tem tela integrada
- Atualizar quantidade → 200
- Atualizar monitor de uma configuração → 200
- Desvincular configuração → 204
- Vincular modelo de outra instituição → 404 (RLS esconde)
- Vincular monitor de outra instituição → 404 (RLS esconde)

**Migration V12:**
- Aplicar sobre banco com dados de V10/V11 → monitores migrados corretamente
- Aplicar sobre banco vazio → tabelas criadas sem erro
- Constraint unique funciona com `monitor_id` NULL (duas configurações sem monitor para o mesmo modelo+SO são bloqueadas)
