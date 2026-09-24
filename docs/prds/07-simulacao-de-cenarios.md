# PRD 07 — Simulação de cenários alternativos

> Revisado em 2026-09-22 conforme a RFC "Cadastro de equipamentos por composição de componentes vs. modelo monolítico". Com computador e monitor separados, a troca de monitores passa a ser um cenário próprio.

## Contexto

Saber quanto um laboratório emite é útil. Saber quanto ele deixaria de emitir com uma mudança específica é o que sustenta uma decisão de gestão.

O estudo de referência chegou a recomendações concretas (trocar o parque por equipamentos mais eficientes, migrar para Linux quando a troca não for viável, redistribuir as aulas para os dias de menor emissão), mas teve que argumentar por elas de forma qualitativa, porque não havia como testar as alternativas sem refazer o levantamento. Ao mesmo tempo, os dados que ele produziu mostram que essas mudanças têm efeito grande: Windows 10 consumindo cerca do dobro do Windows 11, e Linux abaixo de ambos.

A literatura também traz um alerta que justifica separar as variáveis com cuidado. No caso da mudança de um supercomputador entre países, a eficiência da infraestrutura melhorava mas a limpeza da rede elétrica piorava, e o resultado líquido foi um aumento de 18% nas emissões. Intuição não basta: o cenário precisa ser calculado.

Como o parque é descrito por configurações de computador, sistema operacional e monitor, cada uma dessas partes pode ser alterada isoladamente no cenário. A simulação usa exatamente a mesma lógica de cálculo do resultado real, só que sobre uma configuração hipotética. Não é uma segunda calculadora.

## Histórias de usuário

- Como **membro da coordenação**, quero simular a substituição de um modelo de computador por outro, mantendo os monitores, para estimar o ganho antes de pedir orçamento.
- Como **membro da coordenação**, quero simular a troca apenas dos monitores, porque é uma compra menor e pode ser feita separadamente.
- Como **membro da coordenação**, quero simular a migração do sistema operacional de um laboratório, para avaliar uma alternativa de custo zero à troca de hardware.
- Como **gestor de laboratório**, quero simular uma redistribuição dos horários de aula, para ver se a mudança de ocupação compensa.
- Como **membro da coordenação**, quero comparar o cenário simulado com a situação real lado a lado, para enxergar a diferença em números absolutos e em percentual.
- Como **membro da coordenação**, quero salvar um cenário e voltar a ele depois, para apresentá-lo em reunião.
- Como **pesquisador**, quero comparar mais de um cenário entre si, para escolher a melhor alternativa.

## Critérios de aceite

- **Dado** que tenho um resultado real calculado, **quando** crio um cenário a partir dele, **então** o cenário começa com uma cópia exata da composição atual.
- **Dado** que estou em um cenário, **quando** substituo o modelo de computador de uma configuração, **então** apenas a parcela de computadores é recalculada, e os monitores permanecem como estavam.
- **Dado** que estou em um cenário, **quando** substituo o monitor de uma configuração, **então** apenas a parcela de monitores é recalculada.
- **Dado** que estou em um cenário, **quando** altero o sistema operacional, **então** é usado o consumo do computador naquele sistema; e, se não houver medição para essa combinação de modelo e sistema, a plataforma avisa que o resultado depende de uma estimativa.
- **Dado** que a configuração original usa medição conjunta, **quando** altero o computador, o sistema ou o monitor no cenário, **então** a medição conjunta deixa de valer para aquela configuração e a plataforma indica de onde vem o novo consumo.
- **Dado** que estou em um cenário, **quando** altero a ocupação do laboratório, **então** o resultado reflete o novo número de horas de uso.
- **Dado** que vejo um cenário calculado, **então** vejo lado a lado o valor real, o valor simulado, a diferença absoluta e a diferença percentual, também separadas entre computadores e monitores.
- **Dado** que altero algo em um cenário, **quando** volto ao resultado real, **então** ele permanece intacto: nada que eu faça no cenário modifica a situação registrada.
- **Dado** que salvo um cenário, **quando** o abro depois, **então** ele preserva as alterações e a comparação com a base sobre a qual foi criado.
- **Dado** que o resultado real sobre o qual um cenário foi criado é recalculado, **quando** abro o cenário, **então** sou informado de que a base mudou.
- **Dado** que crio um cenário sem alterar nada, **quando** o calculo, **então** o resultado é idêntico ao real: a mesma entrada produz a mesma saída.

## Fora do escopo

- Otimização automática: a plataforma não propõe sozinha a melhor configuração.
- Estimativa de custo financeiro da substituição, retorno sobre investimento ou prazo de payback.
- Simulação de troca de componentes internos do computador (processador, memória, placa de vídeo).
- Simulação de emissões de fabricação e descarte dos equipamentos substituídos, que pertence ao escopo 3.
- Catálogo de equipamentos disponíveis no mercado para escolher na simulação.
- Simulação de mudança de matriz energética ou de localidade do laboratório.

## Suposições e perguntas em aberto

- Supõe-se que o cenário usa os mesmos fatores de emissão do período real de referência, para que a comparação isole a variável alterada.
- Supõe-se que o usuário tem em mãos as características do equipamento hipotético; a plataforma não as busca em lugar nenhum.
- Em aberto: para simular a migração de sistema operacional sem medição própria daquele sistema, de onde vem o dado de consumo? Uma possibilidade é usar as diferenças observadas no estudo de referência como referência declarada, mas isso precisa ser validado com a orientação, porque é uma extrapolação.
- Em aberto: o cenário deve permitir alterar o período letivo, ou apenas composição e ocupação?
