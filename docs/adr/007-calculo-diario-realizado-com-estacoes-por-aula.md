# ADR-007: Cálculo diário do realizado, com estações usadas por aula

- **Data**: 2026-10-05
- **Status**: Aceito
- **Decisores**: Andrey Dedey
- **Tags**: cálculo, ocupação, metodologia, banco-de-dados

## Contexto e Problema

Até aqui, o cálculo de emissões (PRD 06) aplicava o cadastro **atual** do
laboratório e a grade semanal a **todo** o período letivo, do início ao fim.
O resultado era uma projeção, não o que de fato aconteceu:

- Um semestre em andamento já mostrava a emissão dos meses que ainda não
  ocorreram.
- A grade só dizia se a aula acontecia, e o cálculo supunha todas as estações
  do laboratório ligadas em toda aula. Na prática, o uso parcial é o caso
  comum: uma turma de 12 alunos num laboratório de 18 estações.
- Não havia como registrar o que fugiu da grade: aula cancelada, dia com menos
  alunos, aula extra.
- As decomposições por turno e por dia da semana eram rateios proporcionais às
  horas, e não somas reais. Com laboratórios de potências diferentes em turnos
  diferentes, esses rateios ficavam errados.

## Fatores de Decisão

- O gestor quer o número **acumulado até hoje**, e não só a projeção.
- A grade semanal se repete: sem dado novo, a segunda-feira da semana 1 é igual
  à da semana 2. O cálculo é determinístico por dia.
- Registrar o uso real todo dia é caro e, na prática, ficaria incompleto.
- Os computadores não usados numa aula ficam **desligados** nos laboratórios
  estudados.
- A aplicação ainda não está em produção: não há dados a migrar.

## Opções Consideradas

- **A — Snapshot diário por cron**: um job grava o consumo do dia anterior
  numa tabela, congelado.
- **B — Cálculo sob demanda a partir das entradas**: o consumo de cada dia é
  derivado da grade, das exceções registradas, do calendário e do cadastro.
- **C — Registro obrigatório de presença por aula**: o usuário lança o número
  de estações de cada aula, todo dia.

## Decisão

Escolhemos a **Opção B**, com a grade guardando as estações usadas por aula e
um registro **por exceção**.

### Modelo

1. **Grade semanal** (`laboratory_schedule`): cada aula ocupada passa a ter o
   número de **estações usadas** (`stations_used`, em paralelo a
   `occupied_slots`). É o padrão de todas as semanas do período.
2. **Exceções por data** (`class_occurrence`): sobrescrevem a grade numa data
   específica, por aula (turno + horário).
   - `stations_used = 0`: aula cancelada.
   - Número diferente do padrão: aula ajustada.
   - Horário livre na grade: aula extra.
   Sem exceção, vale a grade. Eventos da instituição inteira (greve, recesso)
   continuam sendo feriados do calendário letivo.
3. **Cálculo**: para cada dia letivo, cada aula consome
   `P_estação × estações_usadas × duração_aula`, e a emissão usa o fator do mês.
   Estações não usadas contam como desligadas. Quando o laboratório tem mais de
   uma configuração, as estações usadas são repartidas proporcionalmente à
   quantidade de cada uma. O número de estações é limitado à capacidade atual
   do laboratório.
4. **Realizado e projeção**: o realizado soma os dias **até ontem** (dias
   fechados). A projeção do período soma o realizado com os dias restantes,
   calculados pela grade e pelas exceções já registradas para datas futuras.
   As decomposições (laboratório, turno, dia da semana, mês, modelo, sistema
   operacional) são somas exatas sobre o realizado.

### Por que não as outras

- **A (snapshot diário)** depende de o job rodar todo dia: um dia de servidor
  fora do ar vira um buraco na série, e cadastrar algo esquecido não corrige o
  passado. Também duplicaria a fórmula em dois lugares.
- **C (presença obrigatória)** gera atrito diário e dados incompletos. O
  registro por exceção captura a mesma informação onde ela difere do padrão.

## Consequências

### Positivas

- O número exibido é o realizado, com a projeção ao lado.
- Corrigir um cadastro (equipamento esquecido, potência errada) corrige o
  passado sem nenhum processo extra.
- As decomposições por turno e por dia da semana passam a ser exatas.
- Uma única implementação da fórmula alimenta o cálculo do período e,
  futuramente, o acompanhamento longitudinal (PRD 08).

### Negativas

- Editar o cadastro altera retroativamente o realizado. Isso é aceitável porque
  a troca real de equipamentos é rara nos laboratórios, mas é uma limitação a
  declarar. A imutabilidade da série histórica pedida pelo PRD 08 fica a cargo
  do instantâneo por período letivo, e não do cálculo diário.
- O cálculo percorre todos os dias do período a cada requisição. Com o volume
  esperado (cerca de 100 dias letivos e poucos laboratórios), o custo é
  desprezível.
- A premissa de estação não usada = desligada passa a fazer parte da
  metodologia. O estudo de referência continua reproduzível cadastrando todas
  as aulas com estações iguais à capacidade.

## Referências

- PRD 06 — Cálculo e apresentação das emissões
- PRD 08 — Acompanhamento entre períodos letivos
- Design: telas 1, 5c, 5c-1, 5d, 5e e 6 em `design/TCC_carbon_calculator.pen`
