# PRD 04 — Registro de medições e de especificações de consumo

## Contexto

Esta é a funcionalidade que sustenta a credibilidade de todo o resto. A escolha metodológica do projeto é explícita: o consumo entra por medição física ou por especificação conhecida de hardware, nunca por estimativa de software de monitoramento. Sutton-Parker demonstrou que o software superestimou o consumo entre 48% e 58% frente ao wattímetro, por usar tabelas de consumo desatualizadas e amostrar com menos frequência, além de não enxergar os monitores conectados.

O estudo de referência mediu com wattímetro digital, seguindo um protocolo: intervalos de 12 ou 15 minutos por máquina, com leituras registradas a cada 4 ou 5 minutos, para acompanhar a flutuação do consumo durante uso típico. O protocolo foi construído em etapas, testado e revisado antes da medição final — sinal de que o registro precisa comportar tanto medições exploratórias quanto a definitiva.

Na prática, nem toda instituição vai ter wattímetro disponível de imediato. Por isso a plataforma aceita as duas origens, mas nunca as confunde: todo resultado carrega a informação de como o consumo foi obtido.

## Histórias de usuário

- Como **gestor de laboratório**, quero registrar uma medição de consumo feita com wattímetro para um modelo de equipamento, para que o cálculo use dado real do meu ambiente.
- Como **gestor de laboratório**, quero registrar as condições da medição — duração, intervalo entre leituras, o que estava sendo executado —, para que outra pessoa possa repetir o procedimento.
- Como **gestor de laboratório**, quero registrar medições separadas para o computador e para o monitor, porque foram medidos separadamente.
- Como **gestor de laboratório sem wattímetro**, quero usar as especificações do fabricante como base de consumo, para conseguir um primeiro resultado.
- Como **membro da coordenação**, quero saber se um número veio de medição ou de especificação, para saber quanto peso dar a ele.
- Como **pesquisador**, quero manter várias medições do mesmo modelo, para comparar e escolher a mais representativa.

## Critérios de aceite

- **Dado** que registro uma medição, **quando** informo o modelo de equipamento, a potência observada e a duração da medição, **então** a medição é registrada e vinculada àquele modelo.
- **Dado** que registro uma medição, **quando** informo a data em que ela foi feita, **então** essa data é usada para determinar qual fator de emissão se aplica.
- **Dado** que um modelo tem medição registrada e também especificação de fabricante, **quando** o cálculo é executado, **então** a medição é usada e a especificação é ignorada.
- **Dado** que um modelo tem apenas especificação de fabricante, **quando** o cálculo é executado, **então** o resultado é produzido e sinalizado como estimativa baseada em especificação.
- **Dado** que vejo qualquer resultado de emissão, **quando** consulto sua origem, **então** consigo identificar quais modelos entraram por medição e quais por especificação.
- **Dado** que registro mais de uma medição para o mesmo modelo, **quando** faço o cálculo, **então** escolho qual medição usar ou uso a média das medições, de forma explícita.
- **Dado** que registro uma medição com valor muito distante das demais medições daquele modelo, **então** a plataforma alerta sobre a discrepância sem impedir o registro.
- **Dado** que não registrei o consumo do monitor, **quando** vejo o resultado, **então** ele indica que o consumo de monitores não está contabilizado.

## Fora do escopo

- Coleta automática de consumo por software instalado nas máquinas — contraria a base metodológica do trabalho.
- Integração direta com wattímetros ou medidores inteligentes.
- Leitura contínua ou em tempo real.
- Estimativa de consumo a partir de benchmarks sintéticos.
- Importação de bases públicas de consumo por modelo de computador.

## Suposições e perguntas em aberto

- Supõe-se que a medição representa uso típico de aula, como no estudo de referência, e não pico nem ociosidade.
- Supõe-se que a eficiência da infraestrutura elétrica do ambiente é tratada como neutra, seguindo a recomendação da literatura para equipamentos individuais, em oposição a data centers.
- Em aberto: a plataforma deve orientar o protocolo de medição, sugerindo duração e intervalo com base no que o estudo de referência validou? Isso aumentaria a comparabilidade entre instituições.
- Em aberto: como tratar consumo em estado ocioso ou desligado mas conectado. O estudo de referência simulou uso durante os horários de aula; fora deles, o consumo não é zero.
