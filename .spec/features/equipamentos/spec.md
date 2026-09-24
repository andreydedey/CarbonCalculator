# Spec: Equipamentos

> feature: equipamentos
> status: rascunho

## Contexto

O sistema tem instituições e laboratórios, mas não sabe o que está dentro deles.
O cadastro do parque computacional é o próximo elo da cadeia — sem ele, não é
possível registrar medições de consumo (PRD 04), calcular emissões (PRD 06) nem
simular cenários (PRD 07). A unidade de cadastro é o **modelo de equipamento**,
não a máquina individual; a quantidade e o sistema operacional são informados por
laboratório.

## Histórias

### US-012 — Cadastro de modelo de equipamento

Como gestor de laboratório, quero cadastrar um modelo de equipamento uma vez na
minha instituição, informando suas especificações de hardware e monitor, para não
repetir o mesmo cadastro dezenas de vezes.

#### AC-034 — Modelo criado com dados válidos

- **Dado** que informo nome do modelo, processador, memória e dados do monitor
- **Quando** submeto o formulário de criação
- **Então** o modelo é criado na minha instituição e aparece na listagem

#### AC-035 — Modelo criado sem nome é rejeitado

- **Dado** que não informo o nome do modelo
- **Quando** submeto o formulário
- **Então** a criação é recusada com aviso de nome obrigatório (resposta 400)

#### AC-036 — Alerta quando monitor não é informado

- **Dado** que cadastro um modelo de equipamento
- **Quando** não informo dados do monitor
- **Então** o modelo é criado, mas a resposta indica que não tem monitor (campo `hasMonitor: false`) para que o frontend sinalize a subestimativa

### US-013 — Listagem e busca de modelos

Como gestor de laboratório, quero listar e buscar os modelos cadastrados na minha
instituição, para encontrar rapidamente o modelo que preciso.

#### AC-037 — Listagem paginada de modelos

- **Dado** que existem modelos cadastrados na minha instituição
- **Quando** acesso a listagem de modelos
- **Então** vejo os modelos ordenados por nome, com paginação

#### AC-038 — Busca por nome de modelo

- **Dado** que existem modelos cadastrados
- **Quando** busco por parte do nome de um modelo
- **Então** a listagem retorna apenas modelos cujo nome contém o termo buscado (case-insensitive)

#### AC-039 — Isolamento de modelos entre instituições

- **Dado** que existem modelos em duas instituições diferentes
- **Quando** listo modelos no contexto da instituição A
- **Então** não vejo modelos da instituição B (RLS)

### US-014 — Edição e exclusão de modelo

Como gestor de laboratório, quero editar ou excluir um modelo de equipamento, para
manter o cadastro atualizado.

#### AC-040 — Modelo editado com sucesso

- **Dado** que existe um modelo cadastrado
- **Quando** altero suas especificações (nome, processador, memória, monitor)
- **Então** o modelo é atualizado

#### AC-041 — Modelo sem vínculos pode ser excluído

- **Dado** que um modelo não está vinculado a nenhum laboratório
- **Quando** solicito a exclusão
- **Então** o modelo é removido (resposta 204)

#### AC-042 — Modelo vinculado a laboratório não pode ser excluído

- **Dado** que um modelo está vinculado a pelo menos um laboratório
- **Quando** solicito a exclusão
- **Então** a exclusão é recusada com aviso orientando a desvincular primeiro (resposta 409)

### US-015 — Vinculação de modelo ao laboratório

Como gestor de laboratório, quero vincular um modelo já cadastrado a um laboratório
informando quantidade e sistema operacional, para representar a composição real do
parque.

#### AC-043 — Modelo vinculado ao laboratório com OS e quantidade

- **Dado** que existe um modelo na minha instituição
- **Quando** o associo a um laboratório informando sistema operacional e quantidade
- **Então** o vínculo é criado e aparece na composição do laboratório

#### AC-044 — Quantidade zero ou negativa é rejeitada

- **Dado** que vinculo um modelo a um laboratório
- **Quando** informo quantidade zero ou negativa
- **Então** o registro é recusado (resposta 400)

#### AC-045 — Combinação duplicada é rejeitada

- **Dado** que um modelo já está vinculado a um laboratório com o mesmo sistema operacional
- **Quando** tento criar o mesmo vínculo novamente
- **Então** o registro é recusado com aviso de duplicidade (resposta 409)

#### AC-046 — Mesmo modelo com OSes diferentes no mesmo lab

- **Dado** que um modelo já está vinculado a um laboratório com Windows 11
- **Quando** vinculo o mesmo modelo ao mesmo laboratório com Ubuntu 22.04
- **Então** o vínculo é criado como uma configuração distinta

### US-016 — Composição do laboratório

Como gestor de laboratório, quero ver a composição de um laboratório — modelos,
sistemas operacionais, quantidades e total de máquinas —, para saber o que existe
dentro dele.

#### AC-047 — Composição com totais

- **Dado** que um laboratório tem modelos vinculados
- **Quando** consulto a composição do laboratório
- **Então** vejo a lista de configurações (modelo, OS, quantidade), o total de máquinas e quantos modelos estão sem monitor

#### AC-048 — Alerta de modelos sem monitor na composição

- **Dado** que um laboratório tem pelo menos um modelo sem dados de monitor
- **Quando** consulto a composição
- **Então** a resposta inclui a contagem de modelos sem monitor para que o frontend exiba o alerta de subestimativa

### US-017 — Desvinculação e atualização de configuração

Como gestor de laboratório, quero alterar a quantidade ou o sistema operacional de
uma configuração, ou desvincular um modelo do laboratório, para refletir mudanças
no parque.

#### AC-049 — Quantidade atualizada com sucesso

- **Dado** que um modelo está vinculado a um laboratório
- **Quando** altero a quantidade para um valor positivo
- **Então** a quantidade é atualizada

#### AC-050 — Modelo desvinculado do laboratório

- **Dado** que um modelo está vinculado a um laboratório
- **Quando** solicito a desvinculação
- **Então** o vínculo é removido e o modelo continua existindo na instituição

## Fora de escopo

- Cadastro individual de máquinas (número de série, patrimônio).
- Descoberta automática de hardware por agente.
- Catálogo compartilhado de modelos entre instituições diferentes.
- Dados de fabricação, ciclo de vida ou descarte (escopo 3).
- Equipamentos que não sejam estações de trabalho e monitores.
- Versionamento temporal das configurações.

## Suposições

| ID | Suposição | Status | Resolução |
|---|---|---|---|
| ASM-005 | As unidades de um mesmo modelo dentro de um laboratório são equivalentes em consumo — o estudo de referência assumiu isso. | confirmada | Mesmo pressuposto do artigo base |
| ASM-006 | As especificações são preenchidas manualmente, a partir da ficha do fabricante — não há integração com bases de dados de hardware. | confirmada | Alinhado com o escopo do TCC |
| ASM-007 | Um monitor por modelo é suficiente para representar a realidade de laboratórios de ensino (compra em lote). | confirmada | Decisão documentada no TDD-03 |

## Perguntas em aberto

| ID | Pergunta | Status | Resposta |
|---|---|---|---|
| Q-005 | Se o escopo 3 entrar no trabalho, ano de fabricação e vida útil precisarão ser adicionados ao modelo. Vale adicionar os campos agora como opcionais? | respondida | Não — adicionar via migration quando necessário. Decisão documentada no TDD-03. |
| Q-006 | Até que ponto detalhar a placa de vídeo? O estudo coletou o dado, mas labs de ensino raramente têm GPU dedicada em uso intenso. | respondida | Flag `has_dedicated_gpu` + campo opcional `gpu_model`. Simplificação documentada no TDD-03. |
