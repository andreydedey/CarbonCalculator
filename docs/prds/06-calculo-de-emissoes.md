# PRD 06 — Cálculo e apresentação das emissões

> Revisado em 2026-09-22 conforme a RFC "Cadastro de equipamentos por composição de componentes vs. modelo monolítico". O cálculo passa a somar computador e monitor por configuração, e o resultado passa a mostrar a contribuição de cada parte.

## Contexto

Aqui é onde tudo se encontra. O cálculo em si não é o diferencial do trabalho: a fórmula do escopo 2 é conhecida e já está resolvida de forma genérica por ferramentas existentes. O valor está em aplicá-la automaticamente sobre um cadastro institucional, com o fator de emissão correto de cada mês, e em apresentar o resultado nas dimensões que interessam a quem decide.

O consumo de cada configuração do laboratório é a soma do consumo do computador naquele sistema operacional com o consumo do monitor, ou o valor de uma medição conjunta, quando for ela a escolhida. Esse consumo é multiplicado pela quantidade de estações e pelas horas de ocupação do período letivo.

O estudo de referência definiu as dimensões que foram úteis para decidir: por laboratório, por horário de aula, por dia da semana e por mês. Foi essa decomposição que permitiu recomendações concretas, como redistribuir aulas práticas para os dias de menor emissão, reorganizar horários e priorizar a substituição do parque mais antigo. Com computador e monitor cadastrados em separado, o resultado também mostra o peso de cada um. Essas são as decisões que um gestor consegue tomar: trocar o computador, trocar o monitor ou trocar o sistema operacional.

O estudo também mostrou o valor de traduzir o número. Dizer 767 kg de CO₂ comunica pouco; dizer que equivale a uma viagem de ida e volta de carro entre São Paulo e Fortaleza comunica muito.

## Histórias de usuário

- Como **gestor de laboratório**, quero calcular as emissões de um laboratório em um período letivo, para saber quanto ele emite.
- Como **gestor institucional**, quero ver o total da instituição somando todos os laboratórios, para ter o número que será reportado.
- Como **membro da coordenação**, quero ver a emissão decomposta por laboratório, turno, dia da semana e mês, para identificar onde intervir.
- Como **gestor de laboratório**, quero saber quanto das emissões vem dos computadores e quanto vem dos monitores, para decidir o que substituir primeiro.
- Como **gestor de laboratório**, quero saber quais modelos de computador, quais monitores e quais sistemas operacionais mais contribuem para o total, para priorizar mudanças.
- Como **membro da coordenação**, quero entender o resultado em termos concretos, para conseguir comunicá-lo a quem não trabalha com carbono.
- Como **pesquisador**, quero exportar os resultados, para usá-los em um relatório ou artigo.
- Como **gestor**, quero entender como o número foi obtido, para confiar nele e defendê-lo.

## Critérios de aceite

- **Dado** que um laboratório tem configurações, dados de consumo, ocupação e período letivo definidos, **quando** solicito o cálculo, **então** recebo a energia consumida e a emissão correspondente.
- **Dado** que falta algum desses elementos, **quando** solicito o cálculo, **então** ele não é executado e a plataforma indica exatamente o que está faltando.
- **Dado** que uma configuração usa consumo separado de computador e monitor, **quando** o cálculo é executado, **então** o consumo da estação é a soma dos dois; e **dado** que usa medição conjunta, **então** o valor conjunto é usado sem somar o monitor outra vez.
- **Dado** que uma configuração não tem monitor por decisão confirmada do gestor, **quando** vejo o resultado, **então** ele indica que o consumo de monitores daquela configuração não está contabilizado.
- **Dado** que o período letivo abrange vários meses, **quando** o cálculo é executado, **então** cada mês usa o fator de emissão vigente naquele mês, e não um fator único para todo o período.
- **Dado** que o laboratório está em região de sistema isolado, **quando** o cálculo é executado, **então** é aplicado o fator próprio de sistemas isolados.
- **Dado** que vejo um resultado, **quando** consulto seu detalhamento, **então** vejo a decomposição por laboratório, por horário, por dia da semana e por mês.
- **Dado** que vejo o total, **quando** consulto sua composição, **então** vejo a parcela de computadores e a de monitores, e a contribuição de cada modelo de computador, de cada modelo de monitor e de cada sistema operacional.
- **Dado** que uma configuração usa medição conjunta, **quando** vejo a decomposição entre computadores e monitores, **então** essa configuração aparece como parcela conjunta, sem divisão inventada entre as partes.
- **Dado** que vejo um total, **então** ele é acompanhado de ao menos uma equivalência do cotidiano que torne a grandeza compreensível.
- **Dado** que vejo um resultado, **quando** consulto sua origem, **então** vejo os dados de entrada usados, a origem do consumo de cada parte (medição separada, medição conjunta ou especificação) e o fator de emissão aplicado em cada mês.
- **Dado** que altero a composição ou a ocupação depois de calcular, **quando** volto ao resultado, **então** ele está identificado como desatualizado até que eu recalcule.
- **Dado** que exporto um resultado, **então** o arquivo inclui os valores agregados e a identificação das premissas usadas.

## Fora do escopo

- Emissões de escopo 1 e de escopo 3.
- Contribuição de componentes internos do computador (processador, memória, placa de vídeo) como parcela do resultado.
- Consumo de outros equipamentos do ambiente, como iluminação e climatização.
- Projeção futura de emissões, que é assunto da simulação de cenários.
- Recomendações automáticas de mitigação geradas pela plataforma.
- Compensação de carbono, créditos ou certificação.
- Conversão monetária do consumo elétrico.

## Suposições e perguntas em aberto

- Supõe-se que a eficiência da infraestrutura elétrica é tratada como neutra, seguindo a recomendação da literatura para equipamentos individuais em vez de data centers.
- Supõe-se que o resultado é determinístico: os mesmos dados de entrada produzem sempre o mesmo número.
- Supõe-se que a validação do trabalho (reproduzir os 767 kg do estudo de referência) é possível com esta estrutura: computador medido por modelo e sistema, monitor pela especificação coletada no estudo.
- Em aberto: as equivalências do cotidiano devem ser fixas ou configuráveis? As usadas no estudo de referência são brasileiras e funcionam bem, mas são específicas daquele contexto.
- Em aberto: o resultado deve apresentar alguma faixa de incerteza quando a entrada vem de especificação em vez de medição? Seria academicamente mais honesto, mas exige definir a margem com a orientação.
