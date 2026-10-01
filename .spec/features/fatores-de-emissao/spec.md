# Spec: Fatores de Emissão

> feature: fatores-de-emissao
> status: rascunho

## Contexto

O fator de emissão converte energia consumida (kWh) em carbono equivalente (kgCO₂). Ele é publicado
mensalmente pelo MCTI para o Sistema Interligado Nacional (SIN). Sem o fator de cada mês abrangido
pelo período letivo, o cálculo de emissões não pode ser executado. O ADMIN cadastra os fatores; gestores
e pesquisadores apenas os consultam. A interface precisa indicar quais meses ainda estão sem fator para
que o ADMIN preencha as lacunas antes do cálculo falhar.

## Histórias

### US-033 — Cadastro e manutenção de fatores mensais

Como administrador da plataforma, quero cadastrar, atualizar e remover o fator de emissão
de cada mês, para que os cálculos usem sempre o valor oficial vigente.

#### AC-103 — Fator criado com dados válidos

- **Dado** que sou ADMIN e informo mês de referência, valor positivo e fonte
- **Quando** submeto o cadastro
- **Então** o fator é criado e a resposta retorna 201 com os dados persistidos

#### AC-104 — Fator sem mês de referência é rejeitado

- **Dado** que sou ADMIN
- **Quando** submeto um fator sem informar o mês de referência
- **Então** o cadastro é recusado (resposta 400)

#### AC-105 — Fator com valor zero ou negativo é rejeitado

- **Dado** que sou ADMIN
- **Quando** submeto um fator com valor igual ou menor que zero
- **Então** o cadastro é recusado (resposta 400)

#### AC-106 — Fator sem fonte é rejeitado

- **Dado** que sou ADMIN
- **Quando** submeto um fator sem informar a fonte
- **Então** o cadastro é recusado (resposta 400)

#### AC-107 — Duplicata do mesmo mês é bloqueada

- **Dado** que já existe fator para o mês 2025-06 na instituição
- **Quando** tento cadastrar outro fator para o mesmo mês
- **Então** o cadastro é recusado com aviso de duplicidade (resposta 409)

#### AC-108 — Fator atualizado com sucesso

- **Dado** que existe um fator cadastrado
- **Quando** altero seu valor e/ou fonte
- **Então** o fator é atualizado (resposta 200) com os novos dados

#### AC-109 — Atualização para mês já ocupado por outro fator é bloqueada

- **Dado** que existem fatores para 2025-05 e 2025-06
- **Quando** altero o fator de 2025-05 para usar o mês 2025-06
- **Então** a atualização é recusada com aviso de duplicidade (resposta 409)

#### AC-110 — Fator removido com sucesso

- **Dado** que existe um fator cadastrado
- **Quando** solicito a remoção
- **Então** o fator é removido (resposta 204)

#### AC-111 — Remoção de fator inexistente retorna 404

- **Dado** que um ID não corresponde a nenhum fator da instituição
- **Quando** solicito a remoção desse ID
- **Então** a resposta é 404

### US-034 — Consulta de fatores com controle de acesso

Como gestor ou pesquisador, quero consultar os fatores de emissão usados nos cálculos da
minha instituição, para poder citá-los em relatórios — mas sem poder alterá-los.

#### AC-112 — Listagem retorna fatores em ordem decrescente de competência

- **Dado** que existem fatores cadastrados para a instituição
- **Quando** listo os fatores sem filtro de ano
- **Então** recebo a lista paginada ordenada por mês de referência decrescente (mais recente primeiro)

#### AC-113 — Filtro por ano retorna apenas fatores daquele ano

- **Dado** que existem fatores de 2024 e 2025
- **Quando** listo com parâmetro `?year=2025`
- **Então** a listagem contém apenas fatores cujo mês de referência está em 2025

#### AC-114 — MANAGER não pode criar, atualizar nem remover fatores

- **Dado** que sou MANAGER (não ADMIN)
- **Quando** tento POST, PUT ou DELETE em `/emission-factors`
- **Então** a ação é negada (resposta 403)

#### AC-115 — Fatores da instituição A não são visíveis para a instituição B

- **Dado** que existem fatores nas instituições A e B
- **Quando** listo fatores no contexto da instituição A
- **Então** não vejo nenhum fator da instituição B (isolamento RLS)

### US-035 — Detecção de lacunas na interface

Como administrador, quero ver na página de fatores quais meses do ano selecionado ainda não
têm fator cadastrado, para identificar lacunas antes que bloqueiem um cálculo.

#### AC-116 — Meses sem fator exibidos como "Pendente"

- **Dado** que o ano selecionado é o corrente e falta o fator de pelo menos um mês já passado
- **Quando** a função `computeEmissionFactorRows` é chamada com a lista de fatores e a data atual
- **Então** cada mês ≤ data atual sem fator aparece como uma linha com `status: 'pendente'`

#### AC-117 — Fator mais recente marcado como "Em uso"

- **Dado** que existem fatores cadastrados e pelo menos um tem `referenceMonth` ≤ hoje
- **Quando** `computeEmissionFactorRows` é chamada
- **Então** exatamente um registro recebe `status: 'em-uso'` — o de `referenceMonth` mais recente ≤ hoje

#### AC-118 — Fatores anteriores ao "Em uso" marcados como "Anterior"

- **Dado** que existem múltiplos fatores cadastrados
- **Quando** `computeEmissionFactorRows` é chamada
- **Então** todos os fatores mais antigos que o "Em uso" recebem `status: 'anterior'`

## Fora de escopo

- Sistema isolado (off-grid): removido — SIN apenas
- Fatores anuais: removido — granularidade mínima é mensal
- Coleta automática do site do MCTI
- Fatores de outros países, estados ou distribuidoras
- Endpoint dedicado de lacunas no backend (cálculo feito no frontend)
- Testes de componente React (sem setup de JSDOM no projeto)

## Suposições

| ID | Suposição | Status | Resolução |
|---|---|---|---|
| ASM-023 | Apenas SIN — todos os laboratórios estão no Sistema Interligado Nacional | confirmada | Definido pelo usuário: IFPA e universidades de Belém todas na SIN |
| ASM-024 | Fator para meses futuros pode ser pré-cadastrado — não há restrição de data máxima | confirmada | Útil para fechar período letivo com antecedência |
| ASM-025 | "Pendente" só se aplica a meses ≤ data atual do ano selecionado; meses futuros não são exibidos como lacuna | confirmada | Documentado no TDD-09 |

## Perguntas em aberto

| ID | Pergunta | Status | Resposta |
|---|---|---|---|
| Q-009 | Quando falta o fator de um mês e o cálculo tenta usá-lo, o sistema deve bloquear ou usar o último disponível? | respondida | Bloquear — PRD 06 já implementa esse comportamento via `EmissionFactorNotFoundException` |
