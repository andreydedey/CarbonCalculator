# PRD 08 — Acompanhamento entre períodos letivos

## Contexto

A limitação mais estrutural do estudo de referência não foi metodológica, foi temporal: a análise aconteceu uma única vez. Não há como saber se as emissões da FACOMP subiram ou caíram depois, nem se as recomendações feitas surtiram efeito, porque não existe segundo ponto de comparação.

O acompanhamento longitudinal é o que responde a isso, e é um dos três diferenciais declarados do trabalho. A abordagem é deliberadamente conservadora: nada de captura contínua e automática, cuja imprecisão a literatura já demonstrou. Em vez disso, instantâneos periódicos — um registro por período letivo, congelado, que forma uma série histórica conforme a plataforma vai sendo usada.

A consequência mais importante é a imutabilidade. Se o resultado de 2026.1 mudar porque alguém corrigiu um cadastro em 2027, a série histórica perde o sentido. O instantâneo precisa preservar não só o número, mas as premissas que o produziram.

## Histórias de usuário

- Como **gestor institucional**, quero registrar o resultado de um período letivo como um instantâneo, para que ele entre na série histórica.
- Como **membro da coordenação**, quero ver a evolução das emissões da instituição ao longo dos períodos, para saber se estamos melhorando.
- Como **membro da coordenação**, quero comparar dois períodos específicos, para avaliar o efeito de uma mudança que fizemos entre eles.
- Como **gestor de laboratório**, quero ver a evolução de um laboratório isolado, porque as mudanças costumam acontecer em um de cada vez.
- Como **membro da coordenação**, quero entender por que a emissão mudou entre dois períodos, para distinguir efeito de gestão de efeito de calendário ou de fator de emissão.
- Como **pesquisador**, quero consultar as premissas de um instantâneo antigo, para reproduzir ou auditar aquele número.

## Critérios de aceite

- **Dado** que tenho um resultado calculado para um período letivo, **quando** o registro como instantâneo, **então** ele passa a compor a série histórica da instituição.
- **Dado** que um instantâneo foi registrado, **quando** altero equipamentos, medições ou calendário depois disso, **então** o instantâneo permanece com os valores originais.
- **Dado** que consulto um instantâneo antigo, **então** vejo o resultado e as premissas usadas: parque, ocupação, origem do consumo e fatores de emissão aplicados.
- **Dado** que tenho instantâneos de vários períodos, **quando** abro o acompanhamento, **então** vejo a evolução ao longo do tempo, no nível da instituição e no nível de cada laboratório.
- **Dado** que comparo dois períodos, **então** vejo a variação absoluta e percentual e os principais elementos que mudaram entre eles.
- **Dado** que dois períodos têm quantidades diferentes de dias letivos, **quando** os comparo, **então** essa diferença é apresentada junto da comparação.
- **Dado** que tenho apenas um instantâneo, **quando** abro o acompanhamento, **então** a tela explica que a série se forma com o uso ao longo dos períodos, em vez de apresentar um gráfico vazio.
- **Dado** que registrei um instantâneo por engano, **quando** o removo, **então** a série é recomposta sem ele, e a remoção é uma ação deliberada e distinta de uma edição.

## Fora do escopo

- Monitoramento contínuo ou em tempo real.
- Alertas automáticos por variação das emissões.
- Metas de redução, acompanhamento de plano de ação.
- Comparação entre instituições diferentes.
- Projeção de tendência futura a partir do histórico.
- Retroatividade: reconstruir períodos anteriores a partir de dados inseridos depois.

## Suposições e perguntas em aberto

- Supõe-se que o período letivo, e não o mês ou o ano civil, é a unidade natural do acompanhamento, por ser a unidade em que o uso dos laboratórios realmente se organiza.
- Supõe-se que a série se forma com o uso continuado da plataforma, sem esforço de engenharia adicional.
- Em aberto: quem registra o instantâneo, o usuário de forma explícita ou a plataforma ao fim do período letivo? O registro explícito é mais previsível; o automático garante que a série não tenha buracos.
- Em aberto: um instantâneo pode ser corrigido caso se descubra um erro real de cadastro? A imutabilidade absoluta protege a série, mas perpetua o erro. Uma alternativa é permitir a correção mantendo o registro da versão anterior.
