# TDD — Cadastro do Parque Computacional

| Campo            | Valor                                          |
| ---------------- | ---------------------------------------------- |
| Tech Lead        | Andrey Dedey                                   |
| PRD de origem    | `docs/prds/03-equipamentos.md`                 |
| ADRs relevantes  | ADR-001 (stack), ADR-004 (multi-tenancy RLS)   |
| Status           | Draft                                          |
| Criado em        | 2026-09-21                                     |
| Atualizado em    | 2026-09-21                                     |

---

## Contexto

As entidades de instituição e laboratório já existem e estão funcionais (TDD-02). O próximo elo da cadeia é o parque computacional: saber **quais equipamentos** estão em cada laboratório e em **que quantidade**. Sem essa informação, não existe cálculo de emissão — o consumo de energia depende diretamente do hardware e do sistema operacional em uso.

O PRD define que a unidade de cadastro é o **modelo de equipamento**, não a máquina individual. Um modelo (ex.: "Dell OptiPlex 3070 — i5-9500, 8 GB") é registrado uma vez na instituição e depois associado a um ou mais laboratórios com quantidade e sistema operacional. Isso reflete a realidade observada no estudo de referência: 90 computadores divididos em apenas 4 modelos.

Dois elementos são inegociáveis do ponto de vista metodológico:
- O **monitor** é parte do cadastro do modelo. Omiti-lo é a principal fonte documentada de subestimativa de consumo.
- O **sistema operacional** é atributo da configuração (modelo + laboratório), não do modelo em si. O mesmo hardware pode rodar Windows em um lab e Linux em outro, e o consumo difere substancialmente.

### Decisões resolvidas

- **Placa de vídeo simplificada.** O estudo de referência coletou o dado, mas laboratórios de ensino raramente têm GPU dedicada em uso intenso. O modelo armazena apenas um flag `has_dedicated_gpu` e um campo opcional `gpu_model`. Se o escopo 3 entrar no futuro, um campo mais detalhado pode ser adicionado via migration.
- **Ano de fabricação e vida útil não entram agora.** Pertencem ao escopo 3, ainda não definido. Se forem necessários depois, será uma migration `ALTER TABLE` sem quebra.
- **Monitor como atributos no modelo de equipamento, não entidade separada.** O PRD associa monitor a modelo ("registrar o monitor associado a cada modelo"). Modelar como entidade separada adicionaria complexidade desnecessária para o caso de uso atual (um monitor por modelo).

## Definição do Problema

O sistema tem instituições e laboratórios, mas não sabe o que está dentro deles. O formulário de laboratório já exibe a seção "Equipamentos do Laboratório" com botão desabilitado. Sem o cadastro do parque:

- Não é possível calcular emissões (PRD 06 depende diretamente)
- Não é possível registrar medições de consumo (PRD 04 precisa saber a qual modelo vincular)
- Não é possível simular cenários de troca de OS ou renovação de hardware (PRD 07)

**O que acontece se não resolvermos:**
- Toda a cadeia posterior (medições → calendário → cálculo → simulação) fica bloqueada.

## Escopo

### Dentro do escopo

- Entidade `EquipmentModel` (modelo de equipamento, escopo da instituição)
- Entidade `LaboratoryEquipment` (associação modelo ↔ laboratório, com OS e quantidade)
- CRUD de modelos de equipamento
- Associação e desassociação de modelos a laboratórios
- Alerta visual quando monitor não é informado no modelo
- Reutilização de modelo existente em outro laboratório (informa apenas quantidade e OS)
- Validação de quantidade > 0
- Visualização da composição do laboratório (modelos, quantidades, total)
- Listagem paginada de modelos com busca por nome

### Fora do escopo

- Cadastro individual de máquinas (número de série, patrimônio)
- Descoberta automática de hardware por agente
- Catálogo compartilhado entre instituições
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
│  Modelos: CRUD + listagem paginada com busca            │
│  Lab → Equipamentos: vincular modelo, qty, OS           │
│  Alerta de monitor ausente no formulário                │
│                                                         │
│  Zod schemas  ──→  React Hook Form  ──→  formulários    │
└──────────────────────────┬──────────────────────────────┘
                           │ HTTP (JSON)
                           │ Header: X-Institution-Id
┌──────────────────────────▼──────────────────────────────┐
│  Backend (Spring Boot)                                  │
│                                                         │
│  EquipmentModelController  ──→  EquipmentModelService   │
│  LaboratoryEquipmentController ──→ LabEquipmentService  │
│                                                         │
│  Validação na service layer (quantidade, campos)        │
└──────────────────────────┬──────────────────────────────┘
                           │ JDBC
                           │ SET app.current_institution
┌──────────────────────────▼──────────────────────────────┐
│  PostgreSQL + RLS                                       │
│  equipment_model (RLS por institution_id)               │
│  laboratory_equipment (sem RLS — lab já é filtrado)     │
└─────────────────────────────────────────────────────────┘
```

### Modelo de dados

**Tabela `equipment_model`** (modelo de equipamento — escopo da instituição)

| Coluna              | Tipo             | Restrições                     |
| ------------------- | ---------------- | ------------------------------ |
| `id`                | `UUID`           | PK, gerado automaticamente    |
| `institution_id`    | `UUID`           | FK → institution(id), NOT NULL |
| `name`              | `VARCHAR(255)`   | NOT NULL                       |
| `processor`         | `VARCHAR(255)`   | NULL                           |
| `memory_gb`         | `INTEGER`        | NULL                           |
| `has_dedicated_gpu` | `BOOLEAN`        | NOT NULL, DEFAULT FALSE        |
| `gpu_model`         | `VARCHAR(255)`   | NULL                           |
| `monitor_name`      | `VARCHAR(255)`   | NULL                           |
| `monitor_size_inches` | `NUMERIC(4,1)` | NULL                           |
| `monitor_resolution` | `VARCHAR(20)`   | NULL (ex.: "1920x1080")        |
| `created_at`        | `TIMESTAMP WITH TZ` | NOT NULL                   |
| `updated_at`        | `TIMESTAMP WITH TZ` | NOT NULL                   |

**RLS:** Habilitado em `equipment_model` com policy filtrando por `current_setting('app.current_institution')`. Mesma mecânica já usada em `laboratory`.

**Tabela `laboratory_equipment`** (configuração: modelo + laboratório + OS + quantidade)

| Coluna               | Tipo             | Restrições                              |
| -------------------- | ---------------- | --------------------------------------- |
| `id`                 | `UUID`           | PK, gerado automaticamente             |
| `laboratory_id`      | `UUID`           | FK → laboratory(id), NOT NULL           |
| `equipment_model_id` | `UUID`           | FK → equipment_model(id), NOT NULL      |
| `operating_system`   | `VARCHAR(100)`   | NOT NULL (ex.: "Windows 11")            |
| `quantity`           | `INTEGER`        | NOT NULL, CHECK (quantity > 0)          |
| `created_at`         | `TIMESTAMP WITH TZ` | NOT NULL                            |
| `updated_at`         | `TIMESTAMP WITH TZ` | NOT NULL                            |

**Constraints:**
- UNIQUE(`laboratory_id`, `equipment_model_id`, `operating_system`) — impede duplicar a mesma combinação; permite o mesmo modelo com OSes diferentes no mesmo lab

**RLS:** Não habilitado em `laboratory_equipment`. A tabela `laboratory` já tem RLS; queries sempre partem do lab (JOIN), então o filtro é herdado. Adicionar RLS aqui exigiria `institution_id` redundante na tabela.

**Índices:**
- `equipment_model(institution_id)` — usado pelo RLS e por queries de listagem
- `laboratory_equipment(laboratory_id)` — composição do lab
- `laboratory_equipment(equipment_model_id)` — verificar dependentes antes de excluir modelo

### Decisões de modelagem

- **OS na associação, não no modelo.** O mesmo Dell OptiPlex pode rodar Windows 11 no Lab A e Ubuntu no Lab B. O OS determina o consumo e é o dado que a simulação de cenários mais vai manipular. Colocá-lo no modelo forçaria duplicação de hardware idêntico.
- **Monitor no modelo, não na associação.** O PRD diz "monitor associado a cada modelo". Na prática, instituições compram lotes de computador + monitor juntos. Se futuramente um lab usar monitor diferente, será um novo modelo.
- **Quantidade validada no backend.** O `CHECK (quantity > 0)` no banco garante integridade; a service layer dá a mensagem amigável.
- **Sem soft-delete de modelos.** Modelos sem uso podem ser excluídos. Modelos vinculados a laboratórios não — a exclusão é bloqueada com mensagem orientando a desassociação primeiro.

### API REST

**Modelos de Equipamento** (filtrados por RLS via header `X-Institution-Id`)

| Método | Rota                                  | Descrição                    | Status |
| ------ | ------------------------------------- | ---------------------------- | ------ |
| POST   | `/api/v1/equipment-models`            | Criar modelo                 | 201    |
| GET    | `/api/v1/equipment-models`            | Listar modelos (paginado)    | 200    |
| GET    | `/api/v1/equipment-models/{id}`       | Obter modelo por ID          | 200    |
| PUT    | `/api/v1/equipment-models/{id}`       | Atualizar modelo             | 200    |
| DELETE | `/api/v1/equipment-models/{id}`       | Excluir (sem vínculos)       | 204    |

**Equipamentos do Laboratório** (aninhado sob laboratório)

| Método | Rota                                                  | Descrição                                | Status |
| ------ | ----------------------------------------------------- | ---------------------------------------- | ------ |
| POST   | `/api/v1/laboratories/{labId}/equipment`               | Vincular modelo ao lab (OS + quantidade) | 201    |
| GET    | `/api/v1/laboratories/{labId}/equipment`               | Listar composição do lab                 | 200    |
| PUT    | `/api/v1/laboratories/{labId}/equipment/{id}`          | Atualizar (quantidade, OS)               | 200    |
| DELETE | `/api/v1/laboratories/{labId}/equipment/{id}`          | Desvincular modelo do lab                | 204    |

**Contratos principais:**

```json
// POST /api/v1/equipment-models
// Header: X-Institution-Id: 550e8400-...
// Request
{
  "name": "Dell OptiPlex 3070",
  "processor": "Intel Core i5-9500",
  "memoryGb": 8,
  "hasDedicatedGpu": false,
  "monitorName": "Dell P2419H",
  "monitorSizeInches": 23.8,
  "monitorResolution": "1920x1080"
}

// Response 201
{
  "id": "aaa-...",
  "name": "Dell OptiPlex 3070",
  "processor": "Intel Core i5-9500",
  "memoryGb": 8,
  "hasDedicatedGpu": false,
  "gpuModel": null,
  "monitorName": "Dell P2419H",
  "monitorSizeInches": 23.8,
  "monitorResolution": "1920x1080",
  "hasMonitor": true,
  "createdAt": "2026-09-21T10:00:00Z"
}
```

```json
// POST /api/v1/laboratories/{labId}/equipment
// Header: X-Institution-Id: 550e8400-...
// Request
{
  "equipmentModelId": "aaa-...",
  "operatingSystem": "Windows 11",
  "quantity": 30
}

// Response 201
{
  "id": "bbb-...",
  "equipmentModel": {
    "id": "aaa-...",
    "name": "Dell OptiPlex 3070",
    "hasMonitor": true
  },
  "operatingSystem": "Windows 11",
  "quantity": 30,
  "createdAt": "2026-09-21T10:00:00Z"
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
        "name": "Dell OptiPlex 3070",
        "processor": "Intel Core i5-9500",
        "memoryGb": 8,
        "hasMonitor": true
      },
      "operatingSystem": "Windows 11",
      "quantity": 30
    },
    {
      "id": "ccc-...",
      "equipmentModel": {
        "id": "ddd-...",
        "name": "HP ProDesk 400 G6",
        "processor": "Intel Core i3-9100",
        "memoryGb": 4,
        "hasMonitor": false
      },
      "operatingSystem": "Ubuntu 22.04 LTS",
      "quantity": 15
    }
  ],
  "totalMachines": 45,
  "modelsWithoutMonitor": 1
}
```

**Regras de negócio na API:**
- `DELETE /equipment-models/{id}` retorna `409 Conflict` se o modelo está vinculado a algum laboratório, com mensagem orientando a desassociação
- `POST /laboratories/{labId}/equipment` com `quantity <= 0` retorna `400 Bad Request`
- `POST /laboratories/{labId}/equipment` com combinação duplicada (mesmo modelo + mesmo OS no mesmo lab) retorna `409 Conflict`
- A resposta de listagem da composição inclui `totalMachines` (soma das quantidades) e `modelsWithoutMonitor` (count de modelos sem dados de monitor) para informar o alerta visual
- `GET /equipment-models` aceita `?name=` para busca por nome (like, case-insensitive) e paginação padrão (`page`, `size`, sort por `name` ASC)

### Frontend

**Páginas e componentes:**

| Componente                      | Localização                      | Descrição                                              |
| ------------------------------- | -------------------------------- | ------------------------------------------------------ |
| Página de modelos               | `/equipment-models`              | Listagem paginada + busca + botão "Novo Modelo"        |
| Formulário de modelo            | Dialog                           | Campos do modelo + seção de monitor (com alerta se vazio)|
| Seção equipamentos no lab       | Dentro de `LaboratoryForm`       | Substituir placeholder por formulário funcional         |
| Dialog "Vincular Equipamento"   | Dialog                           | Select de modelo existente + OS + quantidade            |

**Fluxo "Vincular Equipamento" no laboratório:**
1. Usuário clica "Vincular Equipamento" na seção de equipamentos do laboratório
2. Dialog abre com select de modelos da instituição (com busca)
3. Usuário seleciona modelo, informa OS e quantidade
4. Se o modelo não tem monitor, exibe alerta inline: "Este modelo não possui dados de monitor. O consumo calculado ficará subestimado."
5. Ao salvar, a linha aparece na tabela de composição do lab

**Composição do laboratório (tabela):**

| Coluna                | Conteúdo                              |
| --------------------- | ------------------------------------- |
| Modelo                | Nome do modelo                        |
| Sistema operacional   | OS da configuração                    |
| Quantidade            | Unidades                              |
| Monitor               | Nome ou badge "Sem monitor"           |
| Ações                 | Editar quantidade/OS, desvincular     |

Rodapé da tabela: "Total: X máquinas" + alerta se algum modelo não tem monitor.

**Validação de formulários:**
- Schema Zod para criação de modelo: `name` obrigatório, `memoryGb` positivo se informado
- Schema Zod para vinculação: `equipmentModelId` obrigatório, `operatingSystem` obrigatório, `quantity` inteiro > 0

---

## Riscos

| Risco | Impacto | Probabilidade | Mitigação |
|-------|---------|---------------|-----------|
| RLS em `equipment_model` segue o mesmo padrão de `laboratory` — já validado | Baixo | Baixa | Reutilizar a mesma policy e testes existentes |
| Exclusão de modelo vinculado a lab pode quebrar integridade | Alto | Baixa | Bloqueio na service layer + constraint FK com ON DELETE RESTRICT |
| Usuário cadastra muitos modelos parecidos sem perceber que já existe | Médio | Média | Busca por nome na listagem; futuramente, sugestão de autocomplete |
| Dados de monitor opcionais levam a subestimativa silenciosa | Alto | Alta | Alerta visual obrigatório no frontend + campo `modelsWithoutMonitor` na API; PRD exige "confirmação explícita" |
| Modelo de dados precisa mudar para escopo 3 (ano fabricação, vida útil) | Médio | Média | Campos opcionais adicionados via migration; modelo atual não impede a extensão |

---

## Plano de Implementação

| Fase | Tarefa | Descrição | Estimativa |
|------|--------|-----------|------------|
| **1 — Banco** | Migration V10 — equipment_model | Criar tabela `equipment_model` + RLS policy | 0.5d |
| **1 — Banco** | Migration V11 — laboratory_equipment | Criar tabela `laboratory_equipment` + constraints | 0.5d |
| **1 — Banco** | Entities | JPA entities `EquipmentModel` e `LaboratoryEquipment` | 0.5d |
| **2 — Backend** | DTOs + Mappers | Request/Response DTOs e mapeamento | 0.5d |
| **2 — Backend** | EquipmentModelService + Controller | CRUD de modelos, busca paginada, bloqueio de exclusão | 1d |
| **2 — Backend** | LaboratoryEquipmentService + Controller | Vincular/desvincular, validação de quantidade, composição com totais | 1d |
| **2 — Backend** | Specifications | Busca por nome em equipment_model (reutilizar padrão de LaboratorySpecification) | 0.25d |
| **3 — Frontend** | Página de modelos | Listagem paginada + busca + dialog CRUD (reúsa padrões de LaboratoryList) | 1d |
| **3 — Frontend** | Dialog de vinculação | Select de modelo + OS + quantidade no formulário do lab | 1d |
| **3 — Frontend** | Seção equipamentos no lab | Tabela de composição com totais, alerta de monitor, ações | 1d |
| **3 — Frontend** | API client | Funções em `lib/api/equipment-models.ts` e `lib/api/laboratory-equipment.ts` | 0.5d |
| **4 — Testes** | Testes de integração | Endpoints + RLS + validações com Testcontainers | 1d |
| **4 — Testes** | Testes unitários | Services: validações, bloqueio de exclusão, cálculo de totais | 0.5d |

**Estimativa total**: ~9.25 dias úteis

**Dependências entre fases:**
- Fase 1 desbloqueia Fase 2
- Fase 2 desbloqueia Fases 3 e 4
- Fases 3 e 4 podem rodar em paralelo

---

## Estratégia de Testes

| Tipo | Escopo | Abordagem |
|------|--------|-----------|
| **Integração (backend)** | Endpoints REST + RLS + constraints | Requisições HTTP reais contra PostgreSQL em container (Testcontainers) |
| **Unitário (backend)** | Services | Mock dos repositories para testar regras de negócio isoladamente |

**Cenários críticos a testar:**

**Modelos de equipamento:**
- Criar modelo com dados válidos (com monitor) → 201
- Criar modelo sem dados de monitor → 201 (campo `hasMonitor: false` na resposta)
- Criar modelo sem nome → 400
- Listar modelos com busca por nome → retorna apenas matches
- Atualizar modelo existente → 200
- Excluir modelo sem vínculos → 204
- Excluir modelo vinculado a laboratório → 409 Conflict
- `GET /equipment-models` com tenant A não retorna modelos do tenant B (RLS)

**Equipamentos do laboratório:**
- Vincular modelo ao lab com OS e quantidade → 201
- Vincular com quantidade 0 → 400
- Vincular com quantidade negativa → 400
- Vincular combinação duplicada (mesmo modelo + mesmo OS no mesmo lab) → 409
- Vincular mesmo modelo com OS diferente no mesmo lab → 201 (duas configurações)
- Listar composição do lab → resposta com items, totalMachines, modelsWithoutMonitor
- Atualizar quantidade → 200
- Desvincular modelo do lab → 204
- Vincular modelo de outra instituição → 404 (RLS esconde o modelo)
