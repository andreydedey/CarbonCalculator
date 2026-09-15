# PRD 06 — Cálculo e apresentação das emissões

## Contexto

Aqui é onde tudo se encontra. O cálculo em si não é o diferencial do trabalho: a fórmula do escopo 2 é conhecida e já está resolvida de forma genérica por ferramentas existentes. O valor está em aplicá-la automaticamente sobre um cadastro institucional, com o fator de emissão correto de cada mês, e em apresentar o resultado nas mesmas dimensões que interessam a quem decide.

O estudo de referência definiu quais são essas dimensões, e elas foram diretamente úteis: por laboratório, por horário de aula, por dia da semana e por mês. Foi essa decomposição que permitiu recomendações concretas — redistribuir aulas práticas para os dias de menor emissão, reorganizar os horários de uso, priorizar a substituição do parque mais antigo.

O estudo também mostrou o valor de traduzir o número. Dizer 767 kg de CO₂ comunica pouco; dizer que equivale a uma viagem de ida e volta de carro entre São Paulo e Fortaleza comunica muito.

## Histórias de usuário

- Como **gestor de laboratório**, quero calcular as emissões de um laboratório em um período letivo, para saber quanto ele emite.
- Como **gestor institucional**, quero ver o total da instituição somando todos os laboratórios, para ter o número que será reportado.
- Como **membro da coordenação**, quero ver a emissão decomposta por laboratório, turno, dia da semana e mês, para identificar onde intervir.
- Como **membro da coordenação**, quero entender o resultado em termos concretos, para conseguir comunicá-lo a quem não trabalha com carbono.
- Como **gestor de laboratório**, quero saber quais modelos de equipamento mais contribuem para o total, para priorizar a substituição.
- Como **pesquisador**, quero exportar os resultados, para usá-los em um relatório ou artigo.
- Como **gestor**, quero entender como o número foi obtido, para confiar nele e defendê-lo.

## Critérios de aceite

- **Dado** que um laboratório tem equipamentos, dados de consumo, ocupação e período letivo definidos, **quando** solicito o cálculo, **então** recebo a energia consumida e a emissão correspondente.
- **Dado** que falta algum desses elementos, **quando** solicito o cálculo, **então** ele não é executado e a plataforma indica exatamente o que está faltando.
- **Dado** que o período letivo abrange vários meses, **quando** o cálculo é executado, **então** cada mês usa o fator de emissão vigente naquele mês, e não um fator único para todo o período.
- **Dado** que o laboratório está em região de sistema isolado, **quando** o cálculo é executado, **então** é aplicado o fator próprio de sistemas isolados.
- **Dado** que vejo um resultado, **quando** consulto seu detalhamento, **então** vejo a decomposição por laboratório, por horário, por dia da semana e por mês.
- **Dado** que vejo o total, **quando** consulto sua composição, **então** identifico quanto cada modelo de equipamento contribuiu.
- **Dado** que vejo um total, **então** ele é acompanhado de ao menos uma equivalência do cotidiano que torne a grandeza compreensível.
- **Dado** que vejo um resultado, **quando** consulto sua origem, **então** vejo quais dados de entrada foram usados, quais vieram de medição e quais de especificação, e qual fator de emissão foi aplicado.
- **Dado** que altero um equipamento ou a ocupação depois de calcular, **quando** volto ao resultado, **então** ele está identificado como desatualizado até que eu recalcule.
- **Dado** que exporto um resultado, **então** o arquivo inclui os valores agregados e a identificação das premissas usadas.

## Fora do escopo

- Emissões de escopo 1 e de escopo 3.
- Consumo de outros equipamentos do ambiente, como iluminação e climatização.
- Projeção futura de emissões — isso é assunto da simulação de cenários.
- Recomendações automáticas de mitigação geradas pela plataforma.
- Compensação de carbono, créditos ou certificação.
- Conversão monetária do consumo elétrico.

## Suposições e perguntas em aberto

- Supõe-se que a eficiência da infraestrutura elétrica é tratada como neutra, seguindo a recomendação da literatura para equipamentos individuais em vez de data centers.
- Supõe-se que o resultado é determinístico: os mesmos dados de entrada produzem sempre o mesmo número.
- Em aberto: as equivalências do cotidiano devem ser fixas ou configuráveis? As usadas no estudo de referência são brasileiras e funcionam bem, mas são específicas daquele contexto.
- Em aberto: o resultado deve apresentar alguma faixa de incerteza quando a entrada vem de especificação em vez de medição? Seria academicamente mais honesto, mas exige definir a margem com a orientação.
