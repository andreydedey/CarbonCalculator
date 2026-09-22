# PRD 03 — Cadastro do parque computacional

> Revisado em 2026-09-22 conforme a RFC "Cadastro de equipamentos por composição de componentes vs. modelo monolítico". O monitor deixa de ser parte do modelo de computador: passa a ser cadastrado à parte e escolhido na composição de cada laboratório, junto com o sistema operacional.

## Contexto

O consumo de um laboratório não é uniforme. No estudo de referência, os 90 computadores se dividiam em quatro modelos distintos, distribuídos de forma desigual: um laboratório tinha 30 máquinas de um único modelo, outro misturava três modelos diferentes. E a diferença entre eles é grande: o estudo encontrou consumo aproximadamente duas vezes maior no Windows 10 em relação ao Windows 11, distribuições Linux consumindo menos que ambos, e diferenças relevantes entre gerações de hardware.

Isso define a forma do cadastro. O que importa não é a máquina individual, e sim os modelos e quantas unidades de cada configuração existem em cada laboratório. Cadastrar 90 máquinas uma a uma seria trabalho sem retorno.

O cadastro separa o **computador** do **monitor** porque essas são as duas unidades que um wattímetro de tomada consegue medir, cada uma na sua tomada. Os componentes internos do computador (processador, memória, placa de vídeo) não podem ser medidos isoladamente: o que se mede é o computador inteiro. Além disso, o monitor se repete entre computadores diferentes. No estudo de referência, dois modelos de computador HP usavam o mesmo monitor. Se o monitor fizesse parte do modelo de computador, cada combinação exigiria um cadastro novo.

O monitor não pode ficar de fora. Ignorá-lo é a fonte de subestimativa mais bem documentada: nos casos analisados por Sutton-Parker, monitores representavam 69% e 40% dos dispositivos e ficaram fora da contabilidade.

O sistema operacional pertence à composição do laboratório, e não ao modelo de computador. O mesmo modelo roda sistemas diferentes em laboratórios diferentes, e o consumo muda com o sistema. É também a variável que a simulação de cenários mais vai querer alterar.

Assim, um laboratório é descrito por uma lista de **configurações**: um modelo de computador, um sistema operacional, um modelo de monitor e a quantidade de estações com essa combinação.

## Histórias de usuário

- Como **gestor de laboratório**, quero cadastrar um modelo de computador uma vez e reutilizá-lo em qualquer laboratório da minha instituição, para não repetir o mesmo cadastro.
- Como **gestor de laboratório**, quero cadastrar um modelo de monitor uma vez e combiná-lo com qualquer computador, porque o mesmo monitor costuma estar ligado a computadores diferentes.
- Como **gestor de laboratório**, quero descrever cada laboratório como um conjunto de configurações (computador, sistema operacional, monitor e quantidade), para representar o parque como ele realmente é.
- Como **gestor de laboratório**, quero indicar que um computador tem tela integrada, para não ser obrigado a informar um monitor separado.
- Como **gestor de laboratório**, quero registrar uma placa de vídeo dedicada apenas quando ela existir, para que o cadastro continue simples para quem não tem.
- Como **gestor de laboratório**, quero registrar as especificações de hardware do computador e do monitor, para ter uma base de cálculo mesmo antes de conseguir medir.
- Como **gestor de laboratório**, quero registrar a troca do parque de um laboratório, para que o histórico anterior continue correto.

## Critérios de aceite

- **Dado** que cadastro um modelo de computador, **quando** informo identificação, processador, potência de projeto do processador, número de núcleos e memória, **então** o modelo é criado na minha instituição.
- **Dado** que cadastro um modelo de computador, **quando** informo uma placa de vídeo dedicada, **então** a potência de projeto da placa passa a ser obrigatória; e **quando** não informo, **então** o cadastro é concluído normalmente.
- **Dado** que cadastro um modelo de monitor, **quando** informo sua identificação, **então** ele é criado na minha instituição, e a potência nominal do fabricante pode ser informada opcionalmente.
- **Dado** que um modelo de monitor já existe, **quando** monto configurações com computadores diferentes, **então** escolho o mesmo monitor sem redigitar seus dados.
- **Dado** que adiciono uma configuração a um laboratório, **quando** informo computador, sistema operacional, monitor e quantidade, **então** a configuração passa a compor o laboratório.
- **Dado** que informo a quantidade de uma configuração, **quando** o valor é zero ou negativo, **então** o registro é recusado.
- **Dado** que adiciono uma configuração sem monitor, **quando** o computador não tem tela integrada, **então** a plataforma sinaliza que o resultado ficará subestimado e exige confirmação explícita para prosseguir.
- **Dado** que o computador tem tela integrada, **quando** adiciono a configuração, **então** nenhum monitor é pedido e nenhum alerta de subestimativa é exibido.
- **Dado** que um laboratório tem o mesmo computador com sistemas operacionais diferentes, ou com monitores diferentes, **quando** cadastro essa situação, **então** cada combinação é uma configuração distinta.
- **Dado** que tento adicionar uma configuração idêntica a uma já existente no mesmo laboratório, **então** a duplicidade é impedida e a alteração da quantidade existente é oferecida.
- **Dado** que vejo um laboratório, **quando** consulto sua composição, **então** vejo cada configuração, a quantidade de estações de cada uma e o total de estações.
- **Dado** que altero a composição de um laboratório, **quando** existem resultados já calculados, **então** os resultados anteriores permanecem inalterados e a mudança vale a partir do próximo cálculo.
- **Dado** que um modelo de computador ou de monitor está em uso em alguma configuração, **quando** tento excluí-lo, **então** a exclusão é impedida e as configurações que o usam são indicadas.

## Fora do escopo

- Cadastro individual de cada máquina, com número de série ou patrimônio.
- Descoberta automática de hardware por agente instalado nas máquinas.
- Catálogo compartilhado de modelos entre instituições diferentes.
- Consumo medido por componente interno do computador (processador, memória, placa de vídeo). O computador é medido como uma unidade.
- Periféricos além do monitor (teclado, mouse, caixas de som, webcams).
- Dados de fabricação, ciclo de vida ou descarte, que pertencem ao escopo 3, ainda não definido.
- Equipamentos que não sejam estações de trabalho (impressoras, projetores, roteadores, refrigeração).

## Suposições e perguntas em aberto

- Supõe-se que as estações de uma mesma configuração são equivalentes em consumo. O estudo de referência encontrou unidades do mesmo modelo com consumo até três vezes maior que outras; essa variação é tratada no registro de medições (PRD 04), com mais de uma medição por configuração.
- Supõe-se que cada estação tem um único monitor.
- Supõe-se que as especificações são preenchidas manualmente, a partir da ficha do fabricante.
- Em aberto: estações com dois monitores. Uma quantidade de monitores por configuração resolveria, mas nenhum caso real apareceu até agora.
- Em aberto: se o escopo 3 entrar no trabalho, o cadastro precisará de ano de fabricação e expectativa de vida útil. Com computador e monitor separados, esses dados podem ser registrados para cada um, o que é mais preciso, já que os dois costumam ser trocados em momentos diferentes.
