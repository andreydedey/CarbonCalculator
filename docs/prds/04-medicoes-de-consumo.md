# PRD 04 — Registro de medições e de especificações de consumo

> Revisado em 2026-09-22 conforme a RFC "Cadastro de equipamentos por composição de componentes vs. modelo monolítico". As medições passam a ter três alvos possíveis: o computador em um sistema operacional, o monitor, ou os dois juntos.

## Contexto

Esta é a funcionalidade que sustenta a credibilidade de todo o resto. A escolha metodológica do projeto é explícita: o consumo entra por medição física ou por especificação conhecida de hardware, nunca por estimativa de software de monitoramento. Sutton-Parker demonstrou que o software superestimou o consumo entre 48% e 58% frente ao wattímetro, por usar tabelas de consumo desatualizadas e amostrar com menos frequência, além de não enxergar os monitores conectados.

Um wattímetro de tomada mede o que está ligado a ele. Na prática, isso gera três situações: só o computador, só o monitor, ou os dois juntos na mesma tomada. A plataforma registra qual foi o caso, porque isso define para quais configurações a medição vale:

- A medição do **computador** vale para aquele modelo naquele sistema operacional. O estudo de referência mostrou que o mesmo tipo de máquina consumia cerca de duas vezes mais no Windows 10 do que no Windows 11, então uma medição em um sistema não serve para outro.
- A medição do **monitor** vale para aquele modelo de monitor em qualquer configuração.
- A medição **conjunta** vale apenas para a combinação exata de computador, sistema e monitor que estava conectada.

O estudo de referência mediu cada computador sozinho no nobreak, por 12 minutos, com leituras a cada 4 minutos e o wattímetro zerado entre uma máquina e outra. O protocolo foi testado e revisado antes da medição final. O estudo também encontrou unidades do mesmo modelo com consumo até três vezes maior que outras, o que torna importante guardar várias medições da mesma configuração.

Nem toda instituição vai ter wattímetro disponível de imediato. Por isso a plataforma aceita especificações de fabricante como alternativa, mas nunca as confunde com medição: todo resultado informa de onde veio cada consumo.

## Histórias de usuário

- Como **gestor de laboratório**, quero registrar uma medição do computador informando o modelo e o sistema operacional em uso, para que o cálculo use dado real do meu ambiente.
- Como **gestor de laboratório**, quero registrar uma medição do monitor uma vez e vê-la valer para todas as configurações que usam aquele monitor.
- Como **gestor de laboratório**, quero registrar uma medição feita com computador e monitor na mesma tomada, porque nem sempre é possível separar os dois.
- Como **gestor de laboratório**, quero registrar as condições da medição (duração, intervalo entre leituras, o que estava sendo executado e o que estava conectado ao wattímetro), para que outra pessoa possa repetir o procedimento.
- Como **gestor de laboratório sem wattímetro**, quero usar as especificações do fabricante como base de consumo, para conseguir um primeiro resultado.
- Como **membro da coordenação**, quero saber se cada número veio de medição ou de especificação, para saber quanto peso dar a ele.
- Como **pesquisador**, quero manter várias medições da mesma configuração, para lidar com a variação entre unidades do mesmo modelo.

## Critérios de aceite

- **Dado** que registro uma medição do computador, **quando** informo o modelo, o sistema operacional, a potência média observada, a duração e a data, **então** a medição é vinculada àquela combinação de modelo e sistema.
- **Dado** que existe medição de um modelo de computador apenas em outro sistema operacional, **quando** o cálculo é executado para uma configuração com um sistema diferente, **então** essa medição não é reaproveitada, a especificação é usada no lugar e o resultado indica isso.
- **Dado** que registro uma medição do monitor, **quando** informo o modelo de monitor, a potência média observada, a duração e a data, **então** a medição vale para todas as configurações que usam aquele monitor.
- **Dado** que registro uma medição conjunta, **quando** informo computador, sistema operacional e monitor, **então** ela vale apenas para essa combinação, e o consumo do monitor não é somado outra vez no cálculo.
- **Dado** que uma configuração tem medição conjunta e também medições separadas de computador e monitor, **quando** o cálculo é executado, **então** o gestor escolhe qual das duas formas usar, e a plataforma nunca soma ambas.
- **Dado** que existe medição e também especificação para a mesma parte, **quando** o cálculo é executado, **então** a medição é usada.
- **Dado** que só existe especificação, **quando** o cálculo é executado, **então** o consumo do computador é estimado pela potência de projeto do processador (e da placa de vídeo, se houver) e o do monitor pela potência nominal, e o resultado é sinalizado como estimativa.
- **Dado** que uma parte não tem medição nem especificação, **quando** solicito o cálculo, **então** ele não é executado e a pendência é indicada.
- **Dado** que registro mais de uma medição para o mesmo alvo, **quando** faço o cálculo, **então** escolho de forma explícita qual medição usar ou uso a média delas.
- **Dado** que registro uma medição muito distante das demais do mesmo alvo, **então** a plataforma alerta sobre a discrepância sem impedir o registro.
- **Dado** que registro uma medição, **quando** informo sua data, **então** a data fica guardada para rastreabilidade. O fator de emissão aplicado depende dos meses do período letivo calculado, e não da data da medição.
- **Dado** que vejo qualquer resultado, **quando** consulto sua origem, **então** identifico, para cada configuração, se o consumo do computador e o do monitor vieram de medição separada, medição conjunta ou especificação.

## Fora do escopo

- Coleta automática de consumo por software instalado nas máquinas, que contraria a base metodológica do trabalho.
- Medição de componentes internos do computador em separado.
- Integração direta com wattímetros ou medidores inteligentes.
- Leitura contínua ou em tempo real.
- Estimativa de consumo a partir de benchmarks sintéticos.
- Importação de bases públicas de consumo por modelo de computador ou monitor.

## Suposições e perguntas em aberto

- Supõe-se que a medição representa uso típico de aula, como no estudo de referência, e não pico nem ociosidade.
- Supõe-se que o consumo do monitor em uso típico não depende do sistema operacional do computador. Configurações de brilho e de suspensão de tela podem afetar esse consumo; se as medições mostrarem diferença relevante, a medição do monitor também terá que considerar o sistema.
- Supõe-se que a eficiência da infraestrutura elétrica do ambiente é tratada como neutra, seguindo a recomendação da literatura para equipamentos individuais, em oposição a data centers.
- Em aberto: a plataforma deve sugerir o protocolo validado no estudo de referência (equipamento sozinho na tomada, 12 minutos, leitura a cada 4 minutos)? Isso aumentaria a comparabilidade entre instituições.
- Em aberto: vale registrar o nível de brilho do monitor como condição da medição?
- Em aberto: como tratar consumo em estado ocioso ou desligado mas conectado. O estudo de referência simulou uso durante os horários de aula; fora deles, o consumo não é zero.
