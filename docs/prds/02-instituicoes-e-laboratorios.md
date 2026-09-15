# PRD 02 — Cadastro de instituições e laboratórios

## Contexto

O estudo de referência tratou três laboratórios de uma única faculdade. A plataforma precisa representar essa mesma realidade de forma genérica, para qualquer instituição brasileira, sem que nada no modelo pressuponha a UFPA.

Duas informações do laboratório têm peso direto no resultado. A primeira é o tipo de sistema elétrico que o abastece: o fator de emissão brasileiro é nacional e mensal para o Sistema Interligado Nacional, mas regiões de sistemas isolados usam um fator próprio, muito mais alto, por dependerem de geração a diesel. Um laboratório no interior da Amazônia pode ter emissão bem diferente de um em capital, com exatamente o mesmo parque e o mesmo consumo. A segunda é que o laboratório é a unidade natural de agregação: foi assim que o estudo de referência apresentou seus resultados, e é assim que uma coordenação toma decisão.

Há ainda um ponto que o conceito de interface já revelou como lacuna: se a plataforma é multi-institucional, a troca entre instituições precisa ser visível e explícita, não implícita.

## Histórias de usuário

- Como **administrador da plataforma**, quero cadastrar uma nova instituição, para que ela comece a usar a metodologia.
- Como **gestor institucional**, quero cadastrar meus laboratórios com nome, localização e tipo de sistema elétrico, para que o cálculo use o fator de emissão correto.
- Como **gestor institucional**, quero ver todos os meus laboratórios em uma lista única com o estado de cada um, para saber quais já têm dados suficientes para cálculo e quais estão incompletos.
- Como **pesquisador vinculado a mais de uma instituição**, quero alternar entre elas de forma explícita, para não confundir de qual instituição são os dados que estou vendo.
- Como **gestor institucional**, quero desativar um laboratório desmontado, para que ele saia dos cálculos atuais sem apagar o histórico já registrado.

## Critérios de aceite

- **Dado** que cadastro uma instituição, **quando** informo nome, sigla e cidade ou estado, **então** ela é criada e passa a poder receber laboratórios.
- **Dado** que cadastro um laboratório, **quando** não informo o tipo de sistema elétrico, **então** o cadastro não é concluído e a informação é apresentada como obrigatória.
- **Dado** que estou em uma instituição, **quando** olho para a tela, **então** o nome da instituição ativa está sempre visível, sem precisar navegar para descobri-lo.
- **Dado** que tenho acesso a mais de uma instituição, **quando** troco a instituição ativa, **então** todas as listas e resultados passam a refletir a nova instituição.
- **Dado** que vejo a lista de laboratórios, **quando** um deles não tem equipamentos ou não tem dados de consumo, **então** essa pendência é indicada na própria lista.
- **Dado** que desativo um laboratório, **quando** consulto os resultados de períodos anteriores, **então** ele continua aparecendo neles; e **quando** faço um novo cálculo, **então** ele não é incluído.
- **Dado** que tento excluir um laboratório que já tem histórico registrado, **então** a exclusão é impedida e a desativação é oferecida como alternativa.

## Fora do escopo

- Hierarquia intermediária entre instituição e laboratório (campus, faculdade, departamento).
- Importação de laboratórios a partir de sistemas acadêmicos existentes.
- Mapa ou planta do laboratório, posicionamento físico das máquinas.
- Controle patrimonial, número de tombamento, gestão de ativos.
- Registro de outros tipos de ambiente além de laboratórios de computação (salas de aula, servidores, ar-condicionado).

## Suposições e perguntas em aberto

- Supõe-se que o tipo de sistema elétrico é informado pelo próprio gestor e não verificado automaticamente contra uma base oficial.
- Supõe-se que um laboratório pertence a exatamente uma instituição e não é compartilhado.
- Em aberto: laboratórios de uma mesma instituição podem estar em cidades e sistemas elétricos diferentes. O modelo já suporta isso ao colocar o tipo de sistema no laboratório, mas vale confirmar se é realmente esse o nível certo.
- Em aberto: a plataforma deve cobrir apenas laboratórios de ensino, como o estudo de referência, ou também laboratórios de pesquisa, cujo padrão de uso é bem diferente do calendário de aulas?
