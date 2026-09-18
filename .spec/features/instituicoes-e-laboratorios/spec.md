# Spec: Instituições e Laboratórios

> feature: instituicoes-e-laboratorios
> status: rascunho

## Contexto

A plataforma precisa representar instituições e seus laboratórios de ensino de
forma genérica, para qualquer universidade brasileira. Essas são as entidades raiz
do sistema — toda a cadeia posterior (equipamentos, medições, cálculo de emissões)
depende delas. O isolamento entre instituições usa Row-Level Security no PostgreSQL
(ADR-004).

## Histórias

### US-001 — Cadastro de instituição com laboratório vinculado

Como administrador da plataforma, quero cadastrar uma nova instituição já com seu
primeiro laboratório, para que ela comece a usar a metodologia imediatamente.

#### AC-001 — Instituição criada com dados válidos

- **Dado** que informo nome, sigla, cidade e UF válidos, mais o nome do primeiro laboratório
- **Quando** submeto o formulário de criação
- **Então** a instituição e o laboratório são criados numa única operação (transação atômica) e ambos aparecem no sistema

#### AC-002 — Sigla duplicada é rejeitada

- **Dado** que já existe uma instituição com a sigla "UFPA"
- **Quando** tento criar outra instituição com a mesma sigla
- **Então** a criação é recusada com aviso de sigla já em uso (resposta 409)

#### AC-003 — UF inválida é rejeitada

- **Dado** que informo uma UF que não está na lista das 27 unidades federativas
- **Quando** submeto o formulário
- **Então** a criação é recusada com aviso de UF inválida (resposta 400)

### US-002 — Cadastro de laboratórios adicionais

Como gestor institucional, quero cadastrar laboratórios além do primeiro, para
representar todo o meu parque computacional.

#### AC-004 — Laboratório criado com nome

- **Dado** que estou no contexto de uma instituição
- **Quando** crio um laboratório informando apenas o nome
- **Então** o laboratório é criado e aparece na lista de laboratórios daquela instituição

#### AC-005 — Laboratório sem nome é rejeitado

- **Dado** que estou no contexto de uma instituição
- **Quando** tento criar um laboratório sem informar o nome
- **Então** a criação é recusada com aviso de campo obrigatório (resposta 400)

### US-003 — Listagem de laboratórios

Como gestor institucional, quero ver todos os meus laboratórios em uma lista,
para saber o estado de cada um.

#### AC-006 — Lista mostra apenas laboratórios ativos por padrão

- **Dado** que existem laboratórios ativos e inativos na minha instituição
- **Quando** acesso a lista de laboratórios
- **Então** vejo apenas os ativos

#### AC-007 — Laboratórios inativos podem ser incluídos na listagem

- **Dado** que existem laboratórios inativos
- **Quando** solicito a lista incluindo inativos (query param `?active=false`)
- **Então** vejo tanto ativos quanto inativos

### US-004 — Troca de instituição ativa

Como pesquisador vinculado a mais de uma instituição, quero alternar entre elas de
forma explícita, para não confundir de qual instituição são os dados que estou vendo.

#### AC-008 — Isolamento por RLS entre instituições

- **Dado** que existem laboratórios na instituição A e na instituição B
- **Quando** consulto a lista de laboratórios com o contexto da instituição A
- **Então** vejo apenas os laboratórios da instituição A, e nenhum dado da instituição B é retornado

#### AC-009 — Requisição sem identificação de instituição é recusada

- **Dado** que faço uma requisição a um endpoint de laboratórios
- **Quando** não envio o header `X-Institution-Id`
- **Então** a requisição é recusada com aviso de que a instituição é obrigatória (resposta 400)

#### AC-010 — Acesso direto a laboratório de outra instituição é negado

- **Dado** que estou no contexto da instituição A
- **Quando** tento acessar diretamente um laboratório da instituição B pelo ID
- **Então** o acesso é negado sem revelar informação sobre o laboratório (resposta 404, não 403)

### US-005 — Desativação e exclusão de laboratórios

Como gestor institucional, quero desativar um laboratório desmontado, para que ele
saia dos cálculos atuais sem apagar o histórico.

#### AC-011 — Desativação preserva o laboratório

- **Dado** que desativo um laboratório
- **Quando** consulto a lista incluindo inativos
- **Então** ele aparece como inativo, e seus dados históricos permanecem íntegros

#### AC-012 — Exclusão bloqueada quando há dependentes

- **Dado** que um laboratório possui equipamentos ou medições vinculadas
- **Quando** tento excluí-lo
- **Então** a exclusão é recusada com aviso sugerindo desativação (resposta 409)

#### AC-013 — Exclusão permitida quando não há dependentes

- **Dado** que um laboratório não possui nenhum registro dependente
- **Quando** o excluo
- **Então** ele é removido permanentemente do sistema (resposta 204)

## Fora de escopo

- Autenticação e autorização (PRD 01)
- Tipo de sistema elétrico (SIN/isolado) — adiado, SIN assumido para todos
- Laboratórios de pesquisa
- Hierarquia campus/faculdade/departamento
- Importação de dados de sistemas acadêmicos
- Localização física (mapa, planta, endereço)
- Controle patrimonial
- Campos do design que pertencem a outros PRDs (nº de estações, turno, aulas por dia, emissões)

## Suposições

| ID | Suposição | Status | Resolução |
|---|---|---|---|
| ASM-001 | Todos os laboratórios são do Sistema Interligado Nacional (SIN); não há suporte a sistemas isolados nesta fase | confirmada | Decisão documentada no TDD — o campo será adicionado via migration futura quando houver dados de fator de emissão para sistemas isolados |
| ASM-002 | O laboratório precisa apenas do nome como campo obrigatório; o design mostra uma descrição nos cards da listagem, mas o formulário de criação não pede descrição | confirmada | Alinhado com o usuário — modelo enxuto |
| ASM-003 | O usuário JPA da aplicação não é superuser no PostgreSQL, garantindo que o RLS funcione | confirmada | Requisito do ADR-004 |

## Perguntas em aberto

Nenhuma.
