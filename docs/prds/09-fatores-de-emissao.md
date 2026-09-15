# PRD 09 — Gestão dos fatores de emissão oficiais

## Contexto

O fator de emissão é a variável que converte energia consumida em carbono emitido. No Brasil, ele tem duas características que definem esta funcionalidade por completo.

A primeira: ele é nacional e mensal. Não varia por cidade nem por estado — é calculado mês a mês para o Sistema Interligado Nacional e publicado pelo MCTI. Isso significa que a variável relevante para o cálculo é o **mês**, não a localização. Um laboratório em Belém e outro em Porto Alegre usam o mesmo fator no mesmo mês.

A segunda: há exceção. Regiões de sistemas isolados, principalmente no interior da Amazônia, não estão conectadas ao sistema nacional e dependem de geração a diesel, com fator próprio e substancialmente mais alto. Ignorar isso subestimaria de forma grave a emissão de instituições justamente nessas regiões — um problema concreto para uma plataforma criada no Pará.

Como o dado é oficial e igual para todos, ele é mantido no nível da plataforma, não de cada instituição.

## Histórias de usuário

- Como **administrador da plataforma**, quero registrar o fator de emissão de cada mês, para que os cálculos usem o valor oficial vigente.
- Como **administrador da plataforma**, quero registrar separadamente o fator do sistema interligado e o de sistemas isolados, para atender instituições dos dois contextos.
- Como **administrador da plataforma**, quero registrar a fonte e a data de consulta de cada fator, para que o número seja rastreável até a publicação oficial.
- Como **gestor institucional**, quero consultar os fatores usados no meu cálculo, para poder citá-los em um relatório.
- Como **gestor institucional**, quero ser avisado quando falta o fator de algum mês do meu período letivo, para entender por que o cálculo não fecha.
- Como **pesquisador**, quero que os fatores usados em cálculos passados não mudem retroativamente, para que meus resultados continuem reprodutíveis.

## Critérios de aceite

- **Dado** que sou administrador, **quando** registro um fator informando mês, ano, tipo de sistema elétrico e valor, **então** ele passa a valer para os cálculos daquele mês.
- **Dado** que já existe fator para o mesmo mês e tipo de sistema, **quando** tento cadastrar outro, **então** a duplicidade é impedida e a correção do existente é oferecida.
- **Dado** que registro um fator, **quando** não informo a fonte, **então** o registro não é concluído.
- **Dado** que um cálculo abrange um mês sem fator cadastrado, **quando** solicito o cálculo, **então** ele não é executado e a plataforma indica exatamente quais meses estão faltando.
- **Dado** que um laboratório está em região de sistema isolado, **quando** o cálculo é executado, **então** é aplicado o fator de sistemas isolados daquele mês, e não o do sistema interligado.
- **Dado** que sou gestor institucional, **quando** acesso a lista de fatores, **então** consigo consultá-los mas não alterá-los.
- **Dado** que um fator é corrigido depois de um instantâneo ter sido registrado, **quando** consulto o instantâneo, **então** ele mantém o valor que foi usado no momento do registro.
- **Dado** que consulto a lista de fatores, **então** vejo quais meses já estão cobertos e quais ainda faltam, sem precisar conferir um a um.

## Fora do escopo

- Coleta automática dos fatores a partir do site do MCTI.
- Fatores de emissão de outros países.
- Fatores por distribuidora, por estado ou por município.
- Intensidade de carbono variável por hora do dia.
- Fatores de emissão de escopo 1 e de escopo 3.
- Cálculo próprio de fator a partir da matriz elétrica.

## Suposições e perguntas em aberto

- Supõe-se que a atualização é manual e ocorre com a frequência de publicação oficial, o que é aceitável porque o volume é de poucos registros por ano.
- Supõe-se que apenas dois tipos de sistema elétrico bastam: interligado e isolado.
- Em aberto: quando o fator oficial de um mês ainda não foi publicado, a plataforma deve permitir usar o último disponível de forma declarada, ou simplesmente bloquear o cálculo? Bloquear é mais rigoroso; permitir é mais utilizável perto do fim do período letivo.
- Em aberto: os sistemas isolados têm fator único ou variam entre si? Se variarem, o modelo atual, de um único fator para todos os isolados, é uma simplificação que precisa ficar declarada no texto do TCC.
