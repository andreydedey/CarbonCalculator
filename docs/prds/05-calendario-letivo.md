# PRD 05 — Calendário letivo e ocupação dos laboratórios

## Contexto

Uma medição de wattímetro diz quanto uma máquina consome em alguns minutos. Isso não é uma emissão de período letivo. O que transforma um valor pontual em um número anual é saber quantas vezes aquele consumo se repete: quantos dias de aula, em quais turnos, em quais laboratórios.

Essa é a peça que a literatura genérica chama de fator de repetição de uso e que, no contexto institucional, tem nome próprio: calendário letivo. O estudo de referência fez isso manualmente — considerou o turno vespertino, os horários de aula e os meses do período letivo, e agregou os resultados por laboratório, turno, dia da semana e mês. Foi assim que ele descobriu que segundas, quintas e sextas emitiam menos, e que março, com apenas nove dias letivos, parecia enganosamente melhor que abril.

Sem esta funcionalidade, a plataforma seria só mais uma calculadora. Com ela, o resultado é específico da instituição.

## Histórias de usuário

- Como **gestor institucional**, quero cadastrar o período letivo com data de início e fim, para que o cálculo cubra exatamente o intervalo correto.
- Como **gestor institucional**, quero excluir feriados e recessos do período, para não superestimar as emissões.
- Como **gestor de laboratório**, quero informar em quais dias e horários cada laboratório é usado, para refletir a ocupação real e não uma média chutada.
- Como **gestor de laboratório**, quero informar que um laboratório fica ocioso em determinado horário, para que essa diferença apareça no resultado.
- Como **membro da coordenação**, quero ver quantos dias letivos cada mês teve, para interpretar corretamente a comparação entre meses.
- Como **gestor institucional**, quero reaproveitar o calendário de um período anterior como ponto de partida, para não recomeçar do zero a cada semestre.

## Critérios de aceite

- **Dado** que cadastro um período letivo, **quando** informo início e fim, **então** a plataforma calcula quantos dias letivos há em cada mês do período.
- **Dado** que registro um feriado ou recesso dentro do período, **quando** o cálculo é executado, **então** aqueles dias não contam como dias de uso.
- **Dado** que a data final é anterior à inicial, **então** o período não é aceito.
- **Dado** que defino a ocupação de um laboratório, **quando** marco os dias da semana e os horários de uso, **então** essa grade passa a valer para todo o período letivo.
- **Dado** que um laboratório não tem ocupação definida, **quando** tento calcular suas emissões, **então** o cálculo não é executado e a pendência é indicada.
- **Dado** que vejo o resultado por mês, **quando** um mês tem número de dias letivos muito menor que os demais, **então** essa informação é apresentada junto do valor, para evitar leitura equivocada.
- **Dado** que dois laboratórios têm ocupações diferentes no mesmo período, **quando** comparo seus resultados, **então** a diferença de ocupação está visível como parte da explicação.
- **Dado** que copio o calendário de um período anterior, **quando** confirmo, **então** um novo período é criado com as mesmas datas relativas e ocupações, pronto para edição.

## Fora do escopo

- Integração com o calendário acadêmico oficial da instituição.
- Registro de alocação de turmas, disciplinas ou professores.
- Controle de presença ou de uso efetivo por aluno.
- Reserva de laboratório.
- Diferenciação de ocupação por semana dentro do mesmo período letivo — a grade é semanal e uniforme.

## Suposições e perguntas em aberto

- Supõe-se que a ocupação é constante ao longo do período letivo, como assumiu o estudo de referência ao simular uso diário em todos os horários do turno.
- Supõe-se que, durante um horário ocupado, todas as máquinas do laboratório estão em uso.
- Em aberto: como representar ocupação parcial, em que metade das máquinas está ligada. O estudo de referência não enfrentou isso porque simulou ocupação total, mas é a situação mais comum na prática e a suposição atual superestima o resultado.
- Em aberto: o consumo fora dos horários de aula deve ser considerado, com as máquinas ligadas e ociosas? Isso mudaria o número de forma relevante e é uma extensão natural do estudo original.
