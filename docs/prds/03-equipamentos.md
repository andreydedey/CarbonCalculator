# PRD 03 — Cadastro do parque computacional

## Contexto

O consumo de um laboratório não é uniforme. No estudo de referência, os 90 computadores se dividiam em quatro modelos distintos, distribuídos de forma desigual: um laboratório tinha 30 máquinas de um único modelo, outro misturava três modelos diferentes. E a diferença entre eles é grande — o estudo encontrou consumo aproximadamente duas vezes maior no Windows 10 em relação ao Windows 11, e distribuições Linux consumindo menos que ambos, além de diferenças relevantes entre gerações de hardware.

Isso define a forma do cadastro. O que importa não é a máquina individual, é o **modelo de equipamento** e quantas unidades dele existem em cada laboratório. Cadastrar 90 máquinas uma a uma seria trabalho sem retorno: 87 delas seriam cópias.

Dois cuidados vêm da literatura. O monitor precisa ser parte do cadastro, porque ignorá-lo é a fonte de subestimativa mais bem documentada — nos casos analisados por Sutton-Parker, monitores conectados representavam 69% e 40% dos dispositivos e ficaram de fora da contabilidade. E o sistema operacional precisa ser um atributo do parque, não uma nota de rodapé, porque é a variável que a simulação de cenários mais vai querer alterar.

## Histórias de usuário

- Como **gestor de laboratório**, quero cadastrar um modelo de equipamento uma vez e informar quantas unidades dele existem em cada laboratório, para não repetir o mesmo cadastro dezenas de vezes.
- Como **gestor de laboratório**, quero registrar o monitor associado a cada modelo, para que o consumo dele entre no cálculo.
- Como **gestor de laboratório**, quero registrar o sistema operacional em uso, para que a comparação entre sistemas seja possível depois.
- Como **gestor de laboratório**, quero registrar as especificações de hardware do modelo, para ter uma base de cálculo mesmo antes de conseguir medir.
- Como **gestor de laboratório**, quero reaproveitar um modelo já cadastrado em outro laboratório da minha instituição, porque o mesmo computador costuma estar em vários lugares.
- Como **gestor de laboratório**, quero registrar a troca do parque de um laboratório, para que o histórico anterior continue correto.

## Critérios de aceite

- **Dado** que cadastro um modelo de equipamento, **quando** informo identificação do modelo, processador, memória e sistema operacional, **então** o modelo é criado na minha instituição.
- **Dado** que cadastro um modelo, **quando** não informo dados do monitor, **então** a plataforma sinaliza que o resultado ficará subestimado e exige uma confirmação explícita para prosseguir.
- **Dado** que um modelo já existe na minha instituição, **quando** o associo a outro laboratório, **então** informo apenas a quantidade de unidades, sem redigitar as especificações.
- **Dado** que informo a quantidade de unidades de um modelo em um laboratório, **quando** o valor é zero ou negativo, **então** o registro é recusado.
- **Dado** que vejo um laboratório, **quando** consulto sua composição, **então** vejo a lista de modelos, a quantidade de cada um e o total de máquinas.
- **Dado** que altero o sistema operacional de um modelo, **quando** existem resultados já calculados, **então** os resultados anteriores permanecem inalterados e a mudança vale a partir do próximo cálculo.
- **Dado** que dois laboratórios usam o mesmo modelo com sistemas operacionais diferentes, **quando** cadastro essa situação, **então** a plataforma permite representá-la como duas configurações distintas.

## Fora do escopo

- Cadastro individual de cada máquina, com número de série ou patrimônio.
- Descoberta automática de hardware por agente instalado nas máquinas.
- Catálogo compartilhado de modelos entre instituições diferentes.
- Dados de fabricação, ciclo de vida ou descarte do equipamento — pertencem ao escopo 3, ainda não definido.
- Equipamentos que não sejam estações de trabalho e seus monitores (impressoras, projetores, roteadores, refrigeração).

## Suposições e perguntas em aberto

- Supõe-se que as unidades de um mesmo modelo dentro de um laboratório são equivalentes em consumo, assim como assumiu o estudo de referência.
- Supõe-se que as especificações são preenchidas manualmente, a partir da ficha do fabricante.
- Em aberto: se o escopo 3 entrar no trabalho, este cadastro precisará de ano de fabricação e expectativa de vida útil. Vale decidir cedo, porque adicionar depois significa recadastrar o parque.
- Em aberto: até que ponto detalhar a placa de vídeo. O estudo de referência coletou o dado, mas laboratórios de ensino raramente têm placa dedicada em uso intenso.
