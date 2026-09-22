# RFC: Cadastro de equipamentos por composição de componentes vs. modelo monolítico

| Campo            | Valor                                                        |
|------------------|--------------------------------------------------------------|
| **Impacto**      | HIGH                                                         |
| **Status**       | COMPLETE                                                     |
| **Driver**       | @andreydedey — responsável pela proposta                     |
| **Aprovador**    | @andreydedey, @orientador — devem aprovar antes da ação      |
| **Contribuidores** | —                                                          |
| **Informados**   | —                                                            |
| **Prazo**        | TBD                                                          |
| **Recursos**     | [PRD 03 — Cadastro do parque computacional](../prds/03-equipamentos.md), [PRD 04 — Medições de consumo](../prds/04-medicoes-de-consumo.md), [PRD 06 — Cálculo de emissões](../prds/06-calculo-de-emissoes.md), [PRD 07 — Simulação de cenários](../prds/07-simulacao-de-cenarios.md) |
| **Criado**       | 2026-09-21                                                   |
| **Atualizado**   | 2026-09-22                                                   |

---

## Background

### Estado atual

O PRD 03 define o cadastro de equipamentos como **modelo de equipamento monolítico**: uma entidade única que concentra processador, memória, monitor, sistema operacional e todas as especificações relevantes. O gestor cadastra esse modelo uma vez e depois informa quantas unidades dele existem em cada laboratório, junto com o SO em uso. Essa abordagem já está implementada no branch `feat/equipamentos` com migração, entidades, serviços, controllers e frontend completo.

Na implementação atual, a entidade `EquipmentModel` contém campos para CPU (processador, TDP, núcleos), memória RAM, monitor (nome, watts), tipo de equipamento, sistema operacional e descrição — tudo em uma única tabela. A associação com laboratórios acontece via `LaboratoryEquipment`, que vincula modelo + SO + quantidade.

### Problema ou oportunidade

O modelo monolítico tem limitações que se tornam visíveis conforme o uso escala:

1. **Duplicação disfarçada**: Se um laboratório tem 15 máquinas Dell OptiPlex 7090 com monitor Dell P2422H e 15 iguais com monitor LG 24MK430H, o gestor precisa cadastrar dois "modelos de equipamento" que diferem apenas no monitor. O workaround atual (SO na vinculação) resolve apenas o caso de mesmo modelo com SOs diferentes.

2. **Rigidez para medições**: O PRD 04 prevê medições separadas para computador e monitor. Com o modelo monolítico, a medição é vinculada ao modelo inteiro, tornando difícil reaproveitar uma medição de monitor em outro conjunto. Se o monitor HP E231 foi medido com wattímetro, esse dado deveria servir para qualquer máquina que use esse monitor.

3. **Placa de vídeo como componente opcional**: O PRD 03 deixa em aberto "até que ponto detalhar a placa de vídeo". No modelo monolítico, a GPU é mais um campo que polui o formulário para quem não tem placa dedicada.

### Por que agora

O PRD 03 acabou de ser implementado. Alterar a modelagem agora tem custo menor do que depois que medições (PRD 04) e cálculos (PRD 06) estejam construídos sobre a estrutura atual. Cada PRD subsequente adiciona acoplamento à estrutura de equipamentos.

### O que acontece se não decidirmos

- A implementação atual segue para produção com o modelo monolítico.
- PRDs 04 e 06 são construídos sobre essa estrutura, aumentando o custo de migração futura.
- Limitações de duplicação e granularidade afetam a experiência do gestor e a qualidade das análises.

---

## Suposições

| # | Suposição | Responsável | Confiança | Gatilho de invalidação |
|---|-----------|-------------|-----------|----------------------|
| 1 | As unidades fisicamente medíveis com wattímetro de tomada são duas: o computador (gabinete inteiro) e o monitor. A modelagem deve separar exatamente essas duas unidades, e nada abaixo delas. | @andreydedey | Alta | Se um protocolo de medição viável permitir isolar componentes internos (CPU, RAM, GPU) sem abrir o gabinete |
| 2 | O consumo total de uma configuração pode ser decomposto em computador + monitor, somados linearmente | @andreydedey | Alta | Se a interação elétrica entre computador e monitor (ex: USB-C com Power Delivery) tornar a soma incorreta |
| 3 | O consumo do computador depende do sistema operacional instalado | @andreydedey | Alta | Se medições mostrarem que a diferença entre SOs é insignificante para o mesmo hardware — improvável dado que o estudo FACOMP encontrou variação de ~2x |
| 4 | O consumo do monitor em uso típico não depende do sistema operacional | @andreydedey | Média | Se medições mostrarem efeito relevante de brilho adaptativo, suspensão de tela ou frequência de atualização controlados pelo SO |
| 5 | O gestor prefere um formulário de computador simples (sem monitor) com a composição completa feita na vinculação ao laboratório | @andreydedey | Média | Se testes de usabilidade mostrarem que separar o monitor confunde mais do que simplifica |
| 6 | O esforço de refatorar o PRD 03 e a implementação atual (~1 semana) é justificado pelo ganho de flexibilidade | @andreydedey | Média | Se o escopo do TCC não comportar a refatoração dentro do cronograma |

---

## Critérios de decisão

A opção escolhida deve satisfazer os critérios abaixo, listados por prioridade:

| Prioridade | Critério | Descrição | Peso |
|------------|----------|-----------|------|
| 1 | Suficiência para cálculo de carbono | Deve capturar todos os dados necessários para o cálculo de emissões do escopo 2 (PRD 06) | Must-have |
| 2 | Compatibilidade com medições | Deve permitir vincular medições de wattímetro por unidade fisicamente medível — computador e monitor — respeitando que o wattímetro de tomada mede o gabinete inteiro, não componentes internos. A medição do computador deve poder ser diferenciada por SO. | Must-have |
| 3 | Reuso de dados | Componentes ou modelos iguais devem ser cadastrados uma vez e reutilizados em múltiplos laboratórios/configurações | Alta |
| 4 | Simplicidade de uso | O fluxo de cadastro deve ser intuitivo para gestores de laboratório sem background técnico profundo | Alta |
| 5 | Granularidade de análise | O resultado deve permitir identificar a contribuição de computador e monitor separadamente para a emissão total | Média |
| 6 | Esforço de implementação | Preferir a solução com menor custo de desenvolvimento dentro do escopo do TCC | Média |
| 7 | Extensibilidade | Facilidade de adicionar novos tipos de componentes no futuro (ex: escopo 3, novos dispositivos) | Baixa |

**Regra de decisão**: A opção recomendada deve satisfazer todos os Must-haves e pontuar melhor nos critérios de peso Alto e Médio. Trade-offs serão explicitados quando nenhuma opção vencer em todos os critérios.

---

## Dados relevantes

### Do estudo de referência FACOMP (Correa, Cardoso e Kawasaki — UFPA)

- 90 computadores divididos em 4 modelos distintos, distribuição desigual entre laboratórios.
- Consumo ~2x maior no Windows 10 vs Windows 11; Kubuntu consumindo menos que ambos.
- Os modelos HP 1 (i5-4570) e HP 2 (i7-8700) usam o mesmo monitor HP E231. Nenhum modelo de CPU aparece com mais de um monitor nos dados. Não há um caso que só a composição completa (Opção 2) resolveria.
- Unidades do HP 1 chegaram a consumir até 3x mais que outras do mesmo modelo, justificando múltiplas medições por alvo.
- Protocolo de medição: cada computador sozinho no nobreak, 12 min por máquina, leitura a cada 4 min, reset entre máquinas. O consumo do monitor foi coletado como dado de especificação (potência nominal), não por wattímetro.

### De Sutton-Parker (2022)

- Software de monitoramento superestimou o consumo entre 48% e 58% frente ao wattímetro, por usar tabelas de consumo desatualizadas e amostrar com menor frequência.
- Monitores conectados representavam 69% e 40% dos dispositivos nas duas organizações estudadas e ficaram fora da contabilidade do software.
- Fundamenta a escolha metodológica do projeto pela medição física e pela inclusão obrigatória do monitor.

### De Green Algorithms (Lannelongue et al., 2021)

- A fórmula `nc × Pc × uc + nm × Pm` trata núcleos de CPU e memória como **termos independentes e somados**, não agrupados. O consumo da memória depende da quantidade disponível, não do processador ao qual está acoplada.
- Isso invalida a premissa da Opção 2 de que "CPU+RAM" devem ser cadastrados como um par porque o consumo do processador depende da RAM.

### Da implementação atual

- 1 migração, 2 entidades JPA, 2 serviços, 2 controllers, 6 exceções, frontend completo com formulário, cards e tabela de composição.
- Refatorar a modelagem para a Opção 3 impacta ~15 arquivos (menos que a Opção 2 porque a estrutura geral se mantém).

### Do PRD 04

- Medições são registradas por modelo de equipamento, separadamente para computador e monitor.
- Com a separação do monitor, a medição de monitor se vincula naturalmente à entidade Monitor.
- A medição de computador se vincula ao par modelo + SO, porque o SO afeta o consumo.

---

## Opções consideradas

### Opção 1: Modelo monolítico (implementação atual)

**Descrição**:
Manter a abordagem atual onde `EquipmentModel` é uma entidade única com todos os atributos do equipamento (CPU, RAM, monitor, SO, etc.). A vinculação com laboratórios acontece via `LaboratoryEquipment` (modelo + SO + quantidade).

**Como funciona**:
1. Gestor cadastra um modelo de equipamento preenchendo todos os campos em um formulário único.
2. Gestor vincula o modelo a um laboratório informando SO e quantidade.
3. Medições de consumo (PRD 04) são vinculadas ao modelo inteiro.
4. Cálculo de emissão (PRD 06) usa o consumo do modelo inteiro multiplicado pela quantidade.

**Prós**:
- Já implementado — custo zero de desenvolvimento adicional.
- Fluxo de cadastro simples e direto: um formulário, uma ação.
- Modelo mental familiar: "esse computador é um Dell OptiPlex 7090".
- Menor complexidade de banco de dados (2 tabelas).

**Contras**:
- Duplicação quando apenas o monitor muda (ex: mesmo PC com dois monitores diferentes gera dois "modelos").
- Medições vinculadas ao modelo inteiro, dificultando reuso (ex: medição do monitor HP E231 serve para qualquer máquina com esse monitor, mas fica presa ao modelo).
- Não separa a contribuição de computador e monitor no cálculo de emissão.
- Adição de GPU exige novo campo no modelo, poluindo o formulário para quem não tem GPU dedicada.

**Custo estimado**: SMALL
- Esforço: 0 (já implementado)
- Risco: MEDIUM (limitações acumulam conforme PRDs 04 e 06 são construídos)

---

### Opção 2: Composição por componentes

**Descrição**:
Substituir o modelo monolítico por um sistema de **componentes individuais** que são montados em **kits de equipamento**. Cada componente representa uma peça de hardware que impacta o cálculo de carbono: par CPU+RAM, monitor, GPU dedicada. Um kit agrupa componentes para formar uma configuração completa.

**Como funciona**:
1. Gestor cadastra **componentes** individualmente:
   - **CPU+RAM**: processador, TDP, núcleos, memória RAM (agrupados porque o consumo do processador dependeria da RAM disponível).
   - **Monitor**: modelo, consumo em watts.
   - **GPU** (opcional): modelo, TDP.
2. Gestor monta um **kit** selecionando componentes cadastrados (ex: "Kit Lab A" = i7-10700 8GB + Dell P2422H).
3. Gestor vincula o kit a um laboratório informando SO e quantidade.
4. Medições de consumo (PRD 04) são vinculadas ao **componente**, não ao kit.
5. Cálculo de emissão (PRD 06) soma o consumo de cada componente do kit, multiplicado pela quantidade.

**Prós**:
- Elimina duplicação: cada componente cadastrado uma vez, reutilizado em N kits.
- GPU adicionada naturalmente como componente opcional.
- Extensível para novos tipos de componentes.

**Contras**:
- **A premissa central está errada.** A justificativa de agrupar CPU+RAM é que "o consumo do processador depende da RAM disponível", mas no Green Algorithms (Lannelongue et al., 2021) núcleos e memória são termos independentes e somados (`nc × Pc × uc + nm × Pm`).
- **Promete o que o wattímetro não mede.** A contribuição "do processador i7-10700" ou "da memória de 8 GB" só viria de estimativa por TDP ou fórmula teórica. Essa é a abordagem baseada em tabelas de especificação que Sutton-Parker (2022) mostrou superestimar o consumo em 48% a 58%. Adotá-la enfraquece o argumento de validade da medição física que sustenta o TCC.
- **Não há caso real nos dados que a justifique.** No estudo FACOMP, nenhum modelo de CPU aparece com mais de um monitor. A duplicação que existe (mesmo monitor em modelos diferentes) é resolvida pela Opção 3 com menos complexidade.
- Requer reescrever a implementação atual (~25 arquivos).
- Fluxo de cadastro com mais etapas: cadastrar componentes, depois montar kit.
- Modelo mental mais abstrato: "monte um kit" vs "cadastre um computador".
- Mais tabelas no banco (componentes, tipos de componente, kit, kit_componente).
- Risco de over-engineering para o escopo de um TCC.

**Custo estimado**: LARGE
- Esforço: 2-3 semanas (reescrever backend + frontend + migração de dados)
- Risco: MEDIUM (complexidade de UI para montagem de kits)

---

### Opção 3: Modelo de computador + monitor separado, composição no laboratório ⭐ (Aprovada)

**Descrição**:
Separar o monitor em uma entidade própria e mover a escolha de monitor para a **composição do laboratório**, junto com o modelo de computador, o sistema operacional e a quantidade. O modelo de computador deixa de conter dados de monitor e de SO. A GPU fica como campo opcional no modelo de computador (modelo e TDP, nulos quando não houver placa dedicada).

**Como funciona**:
1. Gestor cadastra **monitores** como entidade separada (nome e potência nominal opcional).
2. Gestor cadastra um **modelo de computador** com CPU (processador, TDP, núcleos), memória RAM, tipo de equipamento e, opcionalmente, GPU dedicada (modelo, TDP). Uma flag indica se o computador tem tela integrada (notebooks, all-in-ones).
3. Gestor vincula ao laboratório informando: **modelo de computador + SO + monitor + quantidade**. O monitor é nulo apenas quando o modelo tem tela integrada ou quando o gestor confirma ausência.
4. Medições de consumo (PRD 04):
   - **Computador**: vinculada ao par modelo + SO (porque o SO afeta o consumo).
   - **Monitor**: vinculada à entidade Monitor (independe do SO).
   - **Conjunta**: vinculada a modelo + SO + monitor, quando ambos estiverem na mesma tomada. Nesse caso o cálculo não soma o monitor de novo.
5. Cálculo de emissão (PRD 06) soma consumo do computador (no SO em uso) + consumo do monitor vinculado, multiplicado pela quantidade.

**Prós**:
- **Alinhada com o limite físico da medição.** O wattímetro de tomada mede o gabinete inteiro ou o monitor — exatamente as duas unidades que esta opção separa.
- **Resolve a duplicação real.** No estudo FACOMP, HP 1 e HP 2 compartilham o monitor HP E231. Com o monitor separado, ele é cadastrado uma vez e reutilizado em ambas as configurações.
- **Medição de monitor reutilizável.** Se o HP E231 for medido com wattímetro, esse dado serve para qualquer laboratório que use esse monitor.
- **Medição de computador diferenciada por SO.** O consumo do computador depende do SO (~2x entre Windows 10 e 11 no estudo FACOMP), e a medição captura essa diferença.
- **Granularidade que vira decisão.** O gestor de laboratório troca a máquina, o monitor ou o SO — nunca só o processador de um SFF. Esta opção cobre exatamente essas três decisões.
- GPU adicionada como campo opcional no modelo de computador, sem poluir o formulário de quem não tem placa dedicada.
- Formulário de cadastro de computador fica mais simples (sem monitor, sem SO).
- Compatível com a estrutura atual, com mudanças pontuais.

**Contras**:
- Não resolve duplicação de CPU/RAM entre modelos (menos frequente na prática — nenhum caso nos dados FACOMP).
- GPU continua como campo no modelo de computador (adequado: GPU dedicada é parte do gabinete e medida junto).
- Solução intermediária que pode precisar de nova refatoração se composição completa for necessária — porém a migração a partir desta opção é menor que a partir da Opção 1.
- Granularidade de análise parcial (separa computador e monitor, mas não componentes internos — coerente com o que o wattímetro consegue medir).

**Custo estimado**: MEDIUM
- Esforço: ~1 semana (extrair monitor, ajustar relações, atualizar frontend)
- Risco: LOW

---

### Opção 4: Não fazer nada

**Descrição**:
Manter a implementação atual sem alterações e seguir para os PRDs 04, 05 e 06. Na prática, o efeito é equivalente ao da Opção 1 — o modelo monolítico segue para produção com suas limitações.

**Prós**:
- Nenhum custo imediato.
- Nenhum risco de regressão.
- Progresso no cronograma do TCC.

**Contras**:
- As limitações de duplicação e granularidade permanecem (mesmas da Opção 1).
- PRDs 04 e 06 são construídos sobre a estrutura monolítica, aumentando o custo de migração futura.
- Medições de monitor não serão reutilizáveis entre modelos.

**Custo estimado**: SMALL (imediato) / potencialmente LARGE (longo prazo)

---

## Comparação das opções

| Critério                                      | Opção 1 (Monolítico) | Opção 2 (Composição) | Opção 3 (Híbrida) ⭐ | Opção 4 (Não fazer) |
|-----------------------------------------------|----------------------|----------------------|-----------------------|---------------------|
| Suficiência para cálculo (must)               | ✅ Sim                | ✅ Sim                | ✅ Sim                 | ✅ Sim               |
| Compatibilidade c/ medições físicas (must)    | ⚠️ Parcial           | ❌ Promete abaixo do wattímetro | ✅ Computador + monitor | ⚠️ Parcial          |
| Reuso de dados                                | Baixo                | Alto                 | Alto (monitor)         | Baixo               |
| Simplicidade de uso                           | Alta                 | Baixa                | Alta                   | Alta                |
| Granularidade de análise                      | Baixa                | Teórica (depende de TDP) | Média (computador vs monitor) | Baixa               |
| Esforço de implementação                      | Nenhum               | 2-3 semanas          | ~1 semana              | Nenhum              |
| Extensibilidade                               | Baixa                | Alta                 | Média                  | Baixa               |

---

## Modelo resultante

Referência para o TDD de implementação. Os nomes e campos refletem o código atual no branch `feat/equipamentos` e as alterações decididas.

### `EquipmentModel` (modelo de computador)

Entidade existente, com campos de monitor e SO removidos e GPU adicionada.

| Campo | Tipo | Existia? | Observação |
|-------|------|----------|------------|
| `id` | UUID | Sim | PK, gerado automaticamente |
| `institution` | FK → Institution | Sim | Multi-tenancy, RLS |
| `name` | VARCHAR(255), NOT NULL | Sim | Identificação do modelo (ex: "HP ProDesk 400 G6") |
| `equipmentType` | VARCHAR(50) | Sim | Desktop, Notebook, All-in-One, Servidor |
| `processor` | VARCHAR(255) | Sim | Ex: "Intel Core i7-8700" |
| `tdpWatts` | INTEGER | Sim | TDP do processador |
| `coreCount` | INTEGER | Sim | Número de núcleos |
| `memoryGb` | INTEGER | Sim | RAM em GB |
| `gpuModel` | VARCHAR(255) | **Novo** | Modelo da GPU dedicada, nulo quando não houver |
| `gpuTdpWatts` | INTEGER | **Novo** | TDP da GPU dedicada, nulo quando não houver |
| `hasIntegratedScreen` | BOOLEAN, DEFAULT false | **Novo** | True para notebooks e all-in-ones com tela integrada |
| `description` | TEXT | Sim | Observações livres |
| `monitorName` | — | **Removido** | Migra para entidade `Monitor` |
| `monitorWatts` | — | **Removido** | Migra para entidade `Monitor` |
| `operatingSystem` | — | **Removido** | Migra para `LaboratoryEquipment` (já estava lá) |
| `createdAt`, `updatedAt` | TIMESTAMPTZ | Sim | |

### `Monitor` (nova entidade)

| Campo | Tipo | Observação |
|-------|------|------------|
| `id` | UUID | PK, gerado automaticamente |
| `institution` | FK → Institution | Multi-tenancy, RLS |
| `name` | VARCHAR(255), NOT NULL | Identificação do monitor (ex: "HP E231") |
| `watts` | INTEGER | Potência nominal em watts, nulo se desconhecida |
| `createdAt`, `updatedAt` | TIMESTAMPTZ | |

### `LaboratoryEquipment` (composição do laboratório)

Entidade existente, com FK para monitor adicionada.

| Campo | Tipo | Existia? | Observação |
|-------|------|----------|------------|
| `id` | UUID | Sim | PK |
| `laboratory` | FK → Laboratory | Sim | |
| `equipmentModel` | FK → EquipmentModel | Sim | |
| `operatingSystem` | VARCHAR(100), NOT NULL | Sim | SO em uso nesta configuração |
| `monitor` | FK → Monitor | **Novo** | Nulo quando `equipmentModel.hasIntegratedScreen = true` ou ausência confirmada |
| `quantity` | INTEGER, NOT NULL, > 0 | Sim | |
| `createdAt`, `updatedAt` | TIMESTAMPTZ | Sim | |

**Unicidade**: `(laboratory_id, equipment_model_id, operating_system, monitor_id)` — substitui a atual `(laboratory_id, equipment_model_id, operating_system)`.

### Alvos de medição (PRD 04, ainda não implementado)

| Alvo | Chave | Quando usar |
|------|-------|-------------|
| Computador | modelo + SO | Computador sozinho no nobreak/wattímetro |
| Monitor | monitor | Monitor medido isoladamente |
| Conjunta | modelo + SO + monitor | Computador e monitor na mesma tomada. Nesse caso o cálculo não soma o consumo do monitor de novo |

### Migração (a partir do estado atual)

1. Criar tabela `monitor` com RLS.
2. Criar um `Monitor` por par distinto (`monitorName`, `monitorWatts`) existente em `equipment_model`.
3. Adicionar coluna `monitor_id` (FK, nullable) em `laboratory_equipment`.
4. Preencher `monitor_id` de cada `laboratory_equipment` com o monitor do respectivo `equipment_model`.
5. Adicionar colunas `gpu_model`, `gpu_tdp_watts`, `has_integrated_screen` em `equipment_model`.
6. Remover colunas `monitor_name`, `monitor_watts`, `operating_system` de `equipment_model`.
7. Atualizar constraint unique de `laboratory_equipment` para incluir `monitor_id`.

---

## Itens de ação

| Ação | Responsável | Prazo | Status |
|------|-------------|-------|--------|
| Decidir pela Opção 3 (híbrida) | @andreydedey | 2026-09-22 | ✅ DONE |
| Atualizar PRDs 03, 04, 06 e 07 conforme decisão | @andreydedey | 2026-09-22 | ✅ DONE |
| Apresentar decisão ao orientador para aprovação | @andreydedey | TBD | NOT STARTED |
| Criar TDD de implementação para a nova estrutura | @andreydedey | TBD | NOT STARTED |
| Migrar implementação atual do branch `feat/equipamentos` | @andreydedey | TBD | NOT STARTED |

---

## Outcome

**Decisão**: Opção 3 — Modelo de computador + monitor separado, composição no laboratório.

**Data da decisão**: 2026-09-22

**Decidido por**: @andreydedey

**Racional**:

A decisão se fundamenta no alinhamento entre a modelagem de dados e o que o instrumento de medição (wattímetro de tomada) consegue de fato medir:

1. **Limite físico da medição.** O wattímetro mede o computador inteiro ou o monitor. Processador, memória e placa de vídeo ficam dentro do gabinete e não podem ser medidos em separado. A Opção 3 separa exatamente essas duas unidades — e nada abaixo delas.

2. **A duplicação real está no monitor.** No estudo FACOMP (Correa, Cardoso e Kawasaki, UFPA), os modelos HP 1 (i5-4570) e HP 2 (i7-8700) compartilham o mesmo monitor HP E231. Nenhum modelo de CPU aparece com mais de um monitor nos dados. A Opção 3 resolve esse caso; a Opção 2 resolveria o mesmo caso com complexidade muito maior, sem ganho real.

3. **O consumo do computador depende do SO.** O estudo encontrou consumo ~2x maior no Windows 10 que no Windows 11, e Kubuntu abaixo de ambos. Por isso a medição pertence ao par modelo + SO, não a um componente abstrato.

4. **A Opção 2 promete o que o wattímetro não mede.** A contribuição "do processador i7-10700" ou "da memória de 8 GB" só viria de estimativa por TDP. Essa é a abordagem baseada em tabelas de especificação que Sutton-Parker (2022) mostrou superestimar o consumo em 48% a 58% frente ao wattímetro. Adotá-la enfraqueceria o argumento de validade da medição física que sustenta o TCC.

5. **A justificativa de agrupar CPU+RAM na Opção 2 está errada.** No Green Algorithms (Lannelongue et al., 2021), `nc × Pc × uc + nm × Pm`: núcleos e memória são termos independentes e somados. O consumo da memória depende da quantidade disponível, não do processador.

6. **Monitor como entidade é metodologicamente justificado.** No Sutton-Parker, monitores eram 69% e 40% dos dispositivos nas duas organizações e ficaram fora da contabilidade do software. Separar o monitor garante que ele nunca seja esquecido.

7. **Variação entre unidades.** Unidades do HP 1 chegaram a consumir até 3x mais que outras do mesmo modelo. Isso justifica guardar várias medições por alvo (tratado no PRD 04), e reforça que o alvo de medição deve ser o computador inteiro, não um componente.

8. **Granularidade que vira decisão.** O gestor de laboratório troca a máquina, o monitor ou o SO — nunca só o processador de um SFF. A Opção 3 cobre exatamente essas três decisões.

9. **Custo.** ~1 semana e risco LOW, contra 2-3 semanas e ~25 arquivos da Opção 2. Se o orientador exigir granularidade por componente no futuro, a migração a partir da Opção 3 é menor que a partir da Opção 1.

**Condições / Ressalvas**:
- A decisão foi tomada pelo driver (@andreydedey) e ainda **não foi apresentada ao orientador**, que consta como aprovador. A implementação pode prosseguir, mas a apresentação ao orientador deve acontecer antes da defesa do TCC, e ajustes podem ser necessários caso ele discorde.
- Se medições futuras mostrarem que o consumo do monitor depende significativamente do SO (ex: brilho adaptativo, suspensão de tela), a associação modelo + SO pode precisar ser estendida ao monitor.

**Follow-up**:
- [x] Atualizar PRDs 03, 04, 06 e 07 conforme opção escolhida
- [ ] Apresentar decisão ao orientador
- [ ] Criar TDD de implementação
- [ ] Migrar branch `feat/equipamentos`
- [ ] Comunicar impacto nos PRDs 04 e 06
