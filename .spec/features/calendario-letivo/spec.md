# Spec: Calendário letivo

> feature: calendario-letivo
> status: auditada

## Contexto

O sistema sabe quanto cada estação consome (TDP), mas não sabe por quanto tempo.
O calendário letivo transforma consumo instantâneo (watts) em energia de período (kWh),
informando quantos dias letivos existem, quais são feriados e em quais horários
cada laboratório opera.

Os turnos (Manhã, Tarde, Noite) são configurados por período letivo e definem
a estrutura temporal das aulas: horário início, quantidade de aulas/dia, duração
de cada aula e intervalo entre elas. A ocupação de cada laboratório é registrada
por slot individual de aula (ex: 3 dos 5 horários da manhã na segunda-feira).

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

- **Dado** que existe um período com feriados, turnos e grades cadastrados
- **Quando** excluo o período
- **Então** feriados, turnos e grades associados são excluídos junto

### US-019 — Registrar feriados e recessos

Como gestor institucional, quero excluir feriados e recessos do período,
para não superestimar as emissões.

#### AC-057 — Substituição da lista de feriados com tipo

- **Dado** que existe um período cadastrado
- **Quando** envio uma nova lista de feriados com data, descrição e tipo (NATIONAL, STATE, MUNICIPAL, RECESS) via PUT
- **Então** a lista anterior é substituída integralmente pela nova

#### AC-058 — Rejeição de feriado fora do intervalo

- **Dado** que o período vai de 2024-03-04 a 2024-07-12
- **Quando** tento cadastrar um feriado em 2024-12-25
- **Então** o sistema rejeita com erro de validação (HTTP 400)

### US-024 — Configurar turnos de aula do período

Como gestor institucional, quero definir os turnos de aula (Manhã, Tarde, Noite)
com horários, quantidade de aulas, duração e intervalo, para estruturar a grade
de ocupação dos laboratórios.

#### AC-067 — Substituição da configuração de turnos

- **Dado** que existe um período cadastrado
- **Quando** envio a configuração dos turnos via PUT com shiftType, startTime, classesPerDay, classDurationMinutes, breakDurationMinutes, activeDays e enabled
- **Então** os turnos são salvos e o endTime é calculado automaticamente como start + (aulas × duração) + ((aulas-1) × intervalo)

#### AC-068 — Rejeição de turno com classesPerDay zero

- **Dado** que envio um turno com classesPerDay = 0
- **Quando** tento salvar a configuração
- **Então** o sistema rejeita com erro de validação (HTTP 400)

#### AC-069 — Rejeição de turno duplicado no mesmo período

- **Dado** que envio dois turnos com shiftType = MORNING no mesmo request
- **Quando** tento salvar a configuração
- **Então** o sistema rejeita com erro de validação (HTTP 400)

#### AC-070 — Turno desativado não contabiliza no cálculo

- **Dado** que o turno Noite está com enabled = false
- **Quando** consulto o resumo do período
- **Então** as horas de uso dos slots noturnos não são contabilizadas

### US-020 — Definir grade de ocupação do laboratório

Como gestor de laboratório, quero informar quais slots de aula cada laboratório
usa em cada turno e dia da semana, para refletir a ocupação real.

#### AC-059 — Substituição da grade de ocupação por slots

- **Dado** que existe um período com turnos configurados e um laboratório
- **Quando** envio a grade com entries contendo shiftId, dayOfWeek e occupiedSlots (array de números dos slots ocupados)
- **Então** a grade anterior é substituída pela nova

#### AC-060 — Rejeição de slot fora do range do turno

- **Dado** que o turno Manhã tem classesPerDay = 5
- **Quando** envio occupiedSlots contendo o valor 6
- **Então** o sistema rejeita com erro de validação (HTTP 400)

#### AC-061 — Rejeição de dia fora dos activeDays do turno

- **Dado** que o turno Manhã tem activeDays = [1,2,3,4,5] (Seg–Sex)
- **Quando** envio uma entry com dayOfWeek = 7 (Domingo)
- **Então** o sistema rejeita com erro de validação (HTTP 400)

### US-021 — Consultar resumo de dias letivos e horas de uso

Como membro da coordenação, quero ver quantos dias letivos cada mês teve e quantas
horas cada laboratório operou, para interpretar corretamente os resultados.

#### AC-062 — Cálculo de dias letivos por mês

- **Dado** que o período 2024.1 vai de 04/03 a 12/07 e tem 5 feriados em dias úteis
- **Quando** consulto o resumo do período
- **Então** vejo os dias letivos por mês, descontando fins de semana e feriados

#### AC-063 — Cálculo de horas de uso por slots ocupados

- **Dado** que LABCOMP-01 tem 3 slots de Manhã (50min cada) ocupados seg-sex
- **Quando** consulto o resumo do período
- **Então** vejo as horas de uso por mês = len(occupiedSlots) × classDurationMinutes × diasLetivos ÷ 60

#### AC-064 — Laboratório sem grade retorna zero horas

- **Dado** que LABIA não tem grade de ocupação cadastrada no período
- **Quando** consulto o resumo
- **Então** LABIA aparece com 0 horas em todos os meses

### US-022 — Copiar calendário de período anterior

Como gestor institucional, quero reaproveitar o calendário de um período anterior
como ponto de partida, para não recomeçar do zero a cada semestre.

#### AC-065 — Cópia de período com turnos, feriados e grades

- **Dado** que o período 2024.1 tem turnos, feriados e grades cadastrados
- **Quando** copio o período informando novo nome e novas datas
- **Então** um novo período é criado com os mesmos turnos, feriados (filtrados para o novo intervalo) e mesmas grades

### US-023 — Isolamento entre instituições

Como gestor institucional, quero que meus períodos letivos sejam visíveis apenas
para minha instituição.

#### AC-066 — RLS isola períodos por instituição

- **Dado** que a UFPA tem o período 2024.1 cadastrado
- **Quando** a UNICAMP consulta seus períodos
- **Então** o período da UFPA não aparece

## Fora de escopo

- Ocupação parcial (percentual de máquinas ligadas por slot)
- Consumo fora dos horários de aula (máquinas ociosas)
- Integração com calendário acadêmico oficial da instituição
- Reserva de laboratório ou alocação de turmas/disciplinas
- Diferenciação por semana dentro do mesmo período (a grade é uniforme)
- Turnos customizados além dos 3 fixos (Manhã, Tarde, Noite)

## Suposições

| ID | Suposição | Status | Resolução |
|---|---|---|---|
| ASM-010 | A ocupação é constante ao longo do período letivo — a grade semanal se repete uniformemente | confirmada | Decisão do TDD, consistente com o estudo de referência |
| ASM-011 | Durante um horário ocupado, todas as máquinas do laboratório estão em uso (100% de ocupação) | confirmada | Decisão do TDD, conservadora (superestima levemente) |
| ASM-012 | Apenas domingos nunca são dias letivos; sábados podem ter aulas | confirmada | Resposta do usuário: permitir sábados na grade |
| ASM-013 | O intervalo entre aulas é constante dentro de um turno (ex: sempre 10min) | confirmada | Decisão do TDD e design, campo break_duration_minutes por turno |
| ASM-014 | O horário fim do turno é calculado, não informado manualmente | confirmada | Design mostra "FIM (CALCULADO)" na tabela de turnos |
| ASM-015 | O tipo de feriado (Nacional, Estadual, Municipal, Recesso) é informativo e não altera o cálculo | confirmada | Decisão do TDD — qualquer tipo desconta igualmente o dia |

## Perguntas em aberto

| ID | Pergunta | Status | Resposta |
|---|---|---|---|
| Q-006 | Deve ser possível cadastrar aulas aos sábados? Algumas instituições têm turno de sábado | respondida | Sim, permitir sábados. Grade vai de segunda (1) a sábado (6). Domingos nunca são letivos. |
