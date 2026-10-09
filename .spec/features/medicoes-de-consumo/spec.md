# Spec: Medições de consumo

> feature: medicoes-de-consumo
> status: auditada

## Contexto

O consumo que entra no cálculo de emissões vem de medição física com wattímetro ou, na falta dela,
da especificação do fabricante — nunca de estimativa por software (PRD 04). Um wattímetro mede o
que está ligado a ele: só o computador (vale para aquele modelo naquele sistema operacional), só o
monitor (vale para aquele monitor em qualquer configuração) ou os dois juntos (vale só para aquela
combinação exata). O gestor de laboratório registra as medições; o cálculo escolhe, para cada
configuração, a melhor fonte disponível e informa de onde veio cada número.

## Histórias

### US-036 — Registrar a medição do computador

Como gestor de laboratório, quero registrar uma medição do computador informando o modelo e o
sistema operacional em uso, para que o cálculo use dado real do meu ambiente.

#### AC-119 — Medição do computador fica vinculada ao modelo e ao sistema operacional

- **Dado** que sou gestor e informo modelo, sistema operacional, potência média, duração e data
- **Quando** registro uma medição do tipo computador
- **Então** a medição é criada e aparece na lista vinculada àquele modelo e sistema operacional (resposta 201)

#### AC-120 — Medição recusada quando faltam ou sobram partes para o tipo de alvo

- **Dado** que escolho o tipo de alvo da medição
- **Quando** deixo de informar uma parte exigida (computador: modelo e sistema; monitor: monitor; conjunta: os três) ou informo uma parte que não pertence ao tipo
- **Então** o registro é recusado com uma mensagem dizendo qual parte está errada (resposta 400)

### US-037 — Registrar a medição do monitor uma vez

Como gestor de laboratório, quero registrar uma medição do monitor uma vez e vê-la valer para
todas as configurações que usam aquele monitor.

#### AC-121 — Medição do monitor vale para todas as configurações com aquele monitor

- **Dado** que existe uma medição do monitor M e duas configurações com computadores diferentes usando M
- **Quando** o cálculo resolve o consumo das duas configurações
- **Então** as duas usam a potência medida do monitor M

### US-038 — Registrar a medição conjunta

Como gestor de laboratório, quero registrar uma medição feita com computador e monitor na mesma
tomada, porque nem sempre é possível separar os dois.

#### AC-122 — Medição conjunta vale só para a combinação exata

- **Dado** que existe uma medição conjunta de computador C, sistema S e monitor M
- **Quando** o cálculo resolve uma configuração com C, S e M, e outra com C, S e outro monitor
- **Então** só a primeira usa a medição conjunta; a segunda não a reaproveita

#### AC-123 — Medição conjunta não soma o monitor de novo e tem prioridade

- **Dado** que uma configuração tem medição conjunta e também medições separadas de computador e de monitor
- **Quando** o cálculo resolve o consumo
- **Então** usa só o valor da medição conjunta como consumo total da estação, sem somar o monitor outra vez

### US-039 — Registrar as condições da medição

Como gestor de laboratório, quero registrar as condições da medição (duração, intervalo entre
leituras e o que estava rodando e conectado), para que outra pessoa possa repetir o procedimento.

#### AC-124 — Potência, duração e data são obrigatórias e positivas

- **Dado** que preencho o formulário de medição
- **Quando** deixo sem tipo de alvo ou sem data, ou informo potência ou duração igual ou menor que zero
- **Então** o registro é recusado com a mensagem do campo correspondente (resposta 400)

#### AC-125 — Condições e intervalo entre leituras ficam guardados

- **Dado** que registro uma medição com intervalo entre leituras e a descrição das condições
- **Quando** consulto a medição
- **Então** vejo a duração, o intervalo e as condições exatamente como registrei

### US-040 — Usar a especificação quando não há medição

Como gestor de laboratório sem wattímetro, quero usar as especificações do fabricante como base de
consumo, para conseguir um primeiro resultado — sem que elas se confundam com medição.

#### AC-126 — Sem medição, o cálculo usa a especificação e diz isso

- **Dado** que uma configuração não tem nenhuma medição
- **Quando** o cálculo resolve o consumo
- **Então** usa a potência de projeto do processador mais a da placa de vídeo e a potência nominal do monitor, com origem "especificação"

#### AC-127 — Medição de outro sistema operacional não é reaproveitada

- **Dado** que existe medição do computador C apenas no sistema S1
- **Quando** o cálculo resolve uma configuração com C no sistema S2
- **Então** a medição de S1 é ignorada e a especificação é usada no lugar

#### AC-128 — Medição tem prioridade sobre especificação, parte por parte

- **Dado** que uma configuração tem medição do computador e o monitor só tem especificação
- **Quando** o cálculo resolve o consumo
- **Então** usa a potência medida para o computador e a nominal para o monitor, indicando a origem de cada parte

#### AC-129 — Cálculo bloqueado quando uma parte não tem medição nem especificação

- **Dado** que uma configuração usada em laboratório tem computador sem potência de projeto ou monitor sem potência nominal, e nenhuma medição cobre essa parte
- **Quando** verifico a prontidão do cálculo
- **Então** o cálculo não é executado e a pendência lista a configuração e a parte sem dado

### US-041 — Saber de onde veio cada número

Como membro da coordenação, quero saber se cada consumo veio de medição ou de especificação, para
saber quanto peso dar a ele.

#### AC-130 — O resultado informa a origem do consumo de cada configuração

- **Dado** que calculo as emissões de um período com configurações medidas e não medidas
- **Quando** consulto as fontes de consumo do resultado
- **Então** cada configuração traz a potência usada e a origem (medição conjunta, medição do computador e/ou do monitor, ou especificação)

#### AC-131 — A tela de Emissões mostra a origem em linguagem simples

- **Dado** que o resultado traz a origem técnica de cada configuração
- **Quando** a tela de Emissões a exibe
- **Então** a origem aparece em português ("Medição conjunta", "Medição do computador + especificação do monitor", "Especificação") e as estimativas por especificação ficam destacadas como tal

### US-042 — Manter várias medições do mesmo alvo

Como pesquisador, quero manter várias medições da mesma configuração, para lidar com a variação
entre unidades do mesmo modelo.

#### AC-132 — Várias medições do mesmo alvo ficam guardadas e o cálculo usa a mais recente

- **Dado** que registro duas medições para o mesmo alvo
- **Quando** listo as medições e calculo as emissões
- **Então** as duas aparecem na lista e o cálculo usa a de data mais recente (no mesmo dia, a última registrada)

#### AC-133 — Medição muito distante das demais gera alerta sem impedir o registro

- **Dado** que já existem pelo menos duas medições do mesmo alvo
- **Quando** registro uma nova medição que difere mais de 50% da média delas
- **Então** a medição é registrada e a resposta traz um alerta com o desvio e a média existente

### US-043 — Isolar medições e sistemas operacionais por instituição

Como gestor de uma instituição, quero que medições e sistemas operacionais cadastrados sejam só da
minha instituição, para que dados de outra instituição não interfiram nos meus resultados.

#### AC-134 — Medições de outra instituição não aparecem nem entram no cálculo

- **Dado** que a instituição A registrou medições
- **Quando** a instituição B lista suas medições
- **Então** não vê as medições da instituição A

#### AC-135 — Sistemas operacionais são cadastrados por instituição, sem nome repetido

- **Dado** que a instituição já tem o sistema operacional "Linux"
- **Quando** tenta cadastrar outro "Linux"
- **Então** o cadastro é recusado (resposta 409), enquanto outra instituição consegue cadastrar o seu "Linux"

## Fora de escopo

- Coleta automática de consumo por software, integração com wattímetros e leitura contínua.
- Medição de componentes internos do computador em separado.
- Estimativa por benchmarks sintéticos e importação de bases públicas de consumo.
- Escolha explícita da medição no cálculo (média, medição específica, conjunta vs. separadas) — adiada (Q-010).
- Tela de cadastro de sistemas operacionais — adiada (Q-011).
- Brilho do monitor como condição estruturada e consumo em estado ocioso (Q-013, Q-014).

## Suposições

| ID | Suposição | Status | Resolução |
|---|---|---|---|
| ASM-026 | A medição representa uso típico de aula, e não pico nem ociosidade | confirmada | PRD 04 e TDD 04 adotam o protocolo do estudo de referência |
| ASM-027 | O consumo do monitor em uso típico não depende do sistema operacional do computador | confirmada | PRD 04: medição do monitor vale para qualquer configuração; revisitar se medições mostrarem diferença relevante |
| ASM-028 | A eficiência da infraestrutura elétrica do ambiente é tratada como neutra | confirmada | PRD 04, seguindo a literatura para equipamentos individuais |
| ASM-029 | Entre várias medições do mesmo alvo, a mais recente representa melhor o parque atual | confirmada | TDD 04 define LATEST como padrão; desempate pelo registro mais recente decidido em 2026-10-06 |

## Perguntas em aberto

| ID | Pergunta | Status | Resposta |
|---|---|---|---|
| Q-010 | O gestor escolhe no cálculo qual medição usar (média, uma específica) e entre conjunta e separadas? | respondida | Adiado pelo dono do produto em 2026-10-06; nesta entrega vale a mais recente e a conjunta tem prioridade |
| Q-011 | Os sistemas operacionais são cadastrados por uma tela do app? | respondida | Adiado pelo dono do produto em 2026-10-06; nesta entrega via API e seed |
| Q-012 | A plataforma sugere o protocolo do estudo (12 min, leitura a cada 4 min)? | respondida | Sim (TDD 04): valores sugeridos no formulário, editáveis |
| Q-013 | Vale registrar o brilho do monitor como condição da medição? | respondida | Não como campo estruturado nesta versão (TDD 04); pode ir nas condições |
| Q-014 | Como tratar consumo ocioso ou com o equipamento desligado mas conectado? | respondida | Fora do escopo; estações não usadas contam como desligadas (ADR-007) |
