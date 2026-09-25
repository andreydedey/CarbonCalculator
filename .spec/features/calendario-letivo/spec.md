# Spec: Calendário letivo

> feature: calendario-letivo
> status: rascunho

## Contexto

O sistema sabe quanto cada estação consome (TDP), mas não sabe por quanto tempo.
O calendário letivo transforma consumo instantâneo (watts) em energia de período (kWh),
informando quantos dias letivos existem, quais são feriados e em quais horários
cada laboratório opera.

## Histórias

### US-018 — Cadastrar período letivo

Como gestor institucional, quero cadastrar o período letivo com data de início e fim,
para que o cálculo cubra exatamente o intervalo correto.

#### AC-051 — Criação de período com datas válidas

- **Dado** que sou gestor da instituição
- **Quando** informo nome, data de início e data de fim válidas
- **Então** o período é criado e aparece na listagem da instituição

#### AC-052 — Rejeição de período com data final anterior à inicial

- **Dado** que informo uma data final anterior à data inicial
- **Quando** tento criar o período
- **Então** o sistema rejeita com erro de validação (HTTP 400)

#### AC-053 — Rejeição de período sobreposto

- **Dado** que já existe um período de 2024-03-04 a 2024-07-12
- **Quando** tento criar outro período de 2024-06-01 a 2024-12-15
- **Então** o sistema rejeita com erro de conflito (HTTP 409)

#### AC-054 — Listagem paginada de períodos

- **Dado** que existem períodos cadastrados na instituição
- **Quando** consulto a lista de períodos
- **Então** recebo os períodos paginados, ordenados por data de início descendente

#### AC-055 — Edição de período

- **Dado** que existe um período cadastrado
- **Quando** altero o nome ou as datas
- **Então** o período é atualizado, respeitando as mesmas validações de criação

#### AC-056 — Exclusão de período com cascade

- **Dado** que existe um período com feriados e grades cadastrados
- **Quando** excluo o período
- **Então** feriados e grades associados são excluídos junto

### US-019 — Registrar feriados e recessos

Como gestor institucional, quero excluir feriados e recessos do período,
para não superestimar as emissões.

#### AC-057 — Substituição da lista de feriados

- **Dado** que existe um período cadastrado
- **Quando** envio uma nova lista de feriados via PUT
- **Então** a lista anterior é substituída integralmente pela nova

#### AC-058 — Rejeição de feriado fora do intervalo

- **Dado** que o período vai de 2024-03-04 a 2024-07-12
- **Quando** tento cadastrar um feriado em 2024-12-25
- **Então** o sistema rejeita com erro de validação (HTTP 400)

### US-020 — Definir grade de ocupação do laboratório

Como gestor de laboratório, quero informar em quais dias e horários cada laboratório
é usado, para refletir a ocupação real.

#### AC-059 — Substituição da grade de ocupação

- **Dado** que existe um período e um laboratório
- **Quando** envio uma grade de blocos horários via PUT
- **Então** a grade anterior é substituída pela nova

#### AC-060 — Rejeição de bloco com horário inválido

- **Dado** que envio um bloco com hora final anterior à inicial
- **Quando** tento salvar a grade
- **Então** o sistema rejeita com erro de validação (HTTP 400)

#### AC-061 — Rejeição de blocos sobrepostos no mesmo dia

- **Dado** que envio dois blocos no mesmo dia com horários que se cruzam
- **Quando** tento salvar a grade
- **Então** o sistema rejeita com erro de validação (HTTP 400)

### US-021 — Consultar resumo de dias letivos e horas de uso

Como membro da coordenação, quero ver quantos dias letivos cada mês teve e quantas
horas cada laboratório operou, para interpretar corretamente os resultados.

#### AC-062 — Cálculo de dias letivos por mês

- **Dado** que o período 2024.1 vai de 04/03 a 12/07 e tem 5 feriados em dias úteis
- **Quando** consulto o resumo do período
- **Então** vejo os dias letivos por mês, descontando fins de semana e feriados

#### AC-063 — Cálculo de horas de uso por laboratório por mês

- **Dado** que LABCOMP-01 opera seg-sex 14h-18h (4h/dia)
- **Quando** consulto o resumo do período
- **Então** vejo as horas de uso por mês = dias_letivos_do_mês × 4

#### AC-064 — Laboratório sem grade retorna zero horas

- **Dado** que LABIA não tem grade de ocupação cadastrada no período
- **Quando** consulto o resumo
- **Então** LABIA aparece com 0 horas em todos os meses

### US-022 — Copiar calendário de período anterior

Como gestor institucional, quero reaproveitar o calendário de um período anterior
como ponto de partida, para não recomeçar do zero a cada semestre.

#### AC-065 — Cópia de período com feriados e grades

- **Dado** que o período 2024.1 tem feriados e grades cadastrados
- **Quando** copio o período informando novo nome e novas datas
- **Então** um novo período é criado com os mesmos feriados (filtrados para o novo intervalo) e mesmas grades

### US-023 — Isolamento entre instituições

Como gestor institucional, quero que meus períodos letivos sejam visíveis apenas
para minha instituição.

#### AC-066 — RLS isola períodos por instituição

- **Dado** que a UFPA tem o período 2024.1 cadastrado
- **Quando** a UNICAMP consulta seus períodos
- **Então** o período da UFPA não aparece

## Fora de escopo

- Ocupação parcial (percentual de máquinas ligadas)
- Consumo fora dos horários de aula (máquinas ociosas)
- Integração com calendário acadêmico oficial da instituição
- Reserva de laboratório ou alocação de turmas/disciplinas
- Diferenciação por semana dentro do mesmo período (a grade é uniforme)

## Suposições

| ID | Suposição | Status | Resolução |
|---|---|---|---|
| ASM-010 | A ocupação é constante ao longo do período letivo — a grade semanal se repete uniformemente | confirmada | Decisão do TDD, consistente com o estudo de referência |
| ASM-011 | Durante um horário ocupado, todas as máquinas do laboratório estão em uso (100% de ocupação) | confirmada | Decisão do TDD, conservadora (superestima levemente) |
| ASM-012 | Apenas domingos nunca são dias letivos; sábados podem ter aulas | confirmada | Resposta do usuário: permitir sábados na grade |

## Perguntas em aberto

| ID | Pergunta | Status | Resposta |
|---|---|---|---|
| Q-006 | Deve ser possível cadastrar aulas aos sábados? Algumas instituições têm turno de sábado | respondida | Sim, permitir sábados. Grade vai de segunda (1) a sábado (6). Domingos nunca são letivos. |
