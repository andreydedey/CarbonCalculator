# PRD 01 — Acesso, papéis e isolamento entre instituições

## Contexto

A plataforma nasce multi-institucional: o diferencial acadêmico da proposta é justamente permitir que várias instituições usem a mesma metodologia sem repetir o levantamento de campo uma da outra. Isso cria duas necessidades imediatas.

A primeira é o isolamento. O parque computacional de uma faculdade, seus horários de aula e suas emissões são dados internos daquela instituição. Um gestor de uma universidade não pode ver nem alterar o laboratório de outra.

A segunda é a distinção entre o que é institucional e o que é comum a todos. Os fatores de emissão publicados pelo MCTI/SIRENE são nacionais: valem igualmente para todas as instituições e não fazem sentido como dado editável por cada uma. Precisam de um responsável acima das instituições.

Daí três papéis: quem administra a plataforma, quem gerencia uma instituição e quem apenas consulta os resultados dela.

## Histórias de usuário

- Como **administrador da plataforma**, quero cadastrar instituições e conceder acesso a seus gestores, para que cada uma opere de forma independente.
- Como **gestor institucional**, quero que meus dados sejam visíveis apenas para minha instituição, para não expor informações internas de infraestrutura.
- Como **gestor institucional**, quero registrar outros colegas da minha instituição, para não ser o único ponto de manutenção do cadastro.
- Como **membro da coordenação**, quero acessar os resultados e simulações sem poder alterar o cadastro, para consultar os números sem risco de corromper a base.
- Como **pesquisador de outra universidade**, quero ter minha própria instituição na plataforma, para aplicar a metodologia ao meu contexto sem interferir no de ninguém.

## Critérios de aceite

- **Dado** que sou um usuário não autenticado, **quando** tento acessar qualquer tela de dados, **então** sou direcionado para a autenticação e nada é exibido.
- **Dado** que estou autenticado como gestor da instituição A, **quando** navego pela plataforma, **então** vejo somente laboratórios, equipamentos, medições e resultados da instituição A.
- **Dado** que estou autenticado como gestor da instituição A, **quando** tento acessar diretamente um registro da instituição B, **então** o acesso é negado e nenhuma informação sobre aquele registro é revelada.
- **Dado** que sou um usuário com papel de consulta, **quando** abro uma tela de cadastro, **então** vejo os dados, mas as ações de criar, editar e excluir não estão disponíveis.
- **Dado** que sou administrador da plataforma, **quando** acesso a área de fatores de emissão, **então** consigo editá-los; e **dado** que sou gestor institucional, **quando** acesso a mesma área, **então** apenas consulto.
- **Dado** que sou gestor de uma instituição, **quando** concedo acesso a um colega, **então** ele passa a enxergar exatamente o mesmo conjunto de dados que eu, e nada além.
- **Dado** que um usuário perde o vínculo com a instituição, **quando** seu acesso é revogado, **então** ele deixa de visualizar os dados, mas os registros que criou permanecem íntegros.

## Fora do escopo

- Autocadastro público e aberto de instituições sem aprovação.
- Integração com sistemas de autenticação institucional das universidades.
- Compartilhamento parcial ou seletivo de dados entre instituições.
- Trilha de auditoria detalhada de quem alterou cada campo.
- Hierarquia interna abaixo da instituição (departamentos, cursos) para fins de permissão.

## Suposições e perguntas em aberto

- Supõe-se que uma instituição tem poucos usuários e que um único nível de gestão basta — não há sub-perfis por laboratório.
- Supõe-se que o administrador da plataforma é papel do próprio autor ou de quem hospedar a instância, não uma função comercial.
- Em aberto: um mesmo usuário pode estar vinculado a mais de uma instituição? Isso interessa a pesquisadores que estudem várias, mas complica o isolamento.
- Em aberto: os resultados agregados de uma instituição podem ser públicos, de forma anônima, para permitir comparação entre instituições? Seria um ganho acadêmico, mas levanta uma discussão de consentimento que hoje não existe no pré-projeto.
