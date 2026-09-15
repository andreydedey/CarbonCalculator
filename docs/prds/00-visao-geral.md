# PRDs — Plataforma de Emissões de Carbono do Escopo 2 em Laboratórios Acadêmicos

## Problema

O estudo de Corrêa, Cardoso e Kawasaki (FACOMP/UFPA) demonstrou que é viável estimar as emissões de escopo 2 de laboratórios de computação a partir de medição física de consumo elétrico, chegando a 767 kg de CO₂ em 90 computadores ao longo de um período letivo. A execução, porém, foi pontual e manual: um levantamento de campo, scripts rodados uma única vez e um painel estático. Nenhuma outra instituição consegue reaproveitar o trabalho — para obter o próprio número, precisaria repetir tudo desde o início.

Ferramentas genéricas de cálculo de pegada de carbono computacional resolvem a fórmula, mas não representam instituições, laboratórios, calendário letivo, histórico entre semestres nem a forma como o fator de emissão brasileiro é publicado (nacional, mensal, com exceção dos sistemas isolados).

A plataforma existe para ocupar esse vão: transformar a metodologia manual em um serviço configurável, reaplicável por qualquer laboratório de computação brasileiro.

## Público-alvo

- **Gestor de laboratório** — responsável técnico que cadastra o parque, informa medições e obtém o resultado.
- **Coordenação ou direção de faculdade** — consome os resultados e as simulações para decidir sobre renovação de parque, migração de sistema operacional e distribuição de horários.
- **Administrador da plataforma** — mantém os fatores de emissão oficiais atualizados para todas as instituições.
- **Pesquisador** — replica ou estende o estudo em outra instituição.

## Princípios que atravessam todos os PRDs

1. **O dado vem de medição física ou de especificação de hardware, nunca de monitoramento automático via software.** Softwares de monitoramento superestimaram o consumo entre 48% e 58% e ignoraram os monitores conectados, que representavam 69% e 40% dos dispositivos nas organizações estudadas.
2. **O monitor é parte do equipamento, não um acessório.** Ignorá-lo é a principal fonte conhecida de subestimativa.
3. **O registro é periódico, por instantâneos, não contínuo.** A plataforma não observa nada em tempo real.
4. **O fator de emissão é uma variável, não uma constante.** Varia por mês e por tipo de sistema elétrico (interligado ou isolado).
5. **O calendário letivo é o que transforma uma medição pontual em emissão de um período.** É ele que diz quantas vezes aquele consumo se repete na prática.
6. **Toda instituição é um inquilino independente.** Ninguém enxerga ou edita o dado de outra.

## Lista de PRDs

| # | Documento | Funcionalidade |
|---|---|---|
| 01 | `01-acesso-e-papeis.md` | Acesso, papéis e isolamento entre instituições |
| 02 | `02-instituicoes-e-laboratorios.md` | Cadastro de instituições e laboratórios |
| 03 | `03-equipamentos.md` | Cadastro do parque computacional |
| 04 | `04-medicoes-de-consumo.md` | Registro de medições e de especificações |
| 05 | `05-calendario-letivo.md` | Calendário letivo e ocupação dos laboratórios |
| 06 | `06-calculo-de-emissoes.md` | Cálculo e apresentação das emissões |
| 07 | `07-simulacao-de-cenarios.md` | Simulação de cenários alternativos |
| 08 | `08-acompanhamento-longitudinal.md` | Acompanhamento entre períodos letivos |
| 09 | `09-fatores-de-emissao.md` | Gestão dos fatores de emissão oficiais |

## Questões em aberto que afetam todos os documentos

- **Escopo 3.** A extensão do trabalho para emissões de fabricação e descarte de equipamentos ainda será definida com a orientação. Todos os PRDs tratam apenas do escopo 2. Se o escopo 3 entrar, o cadastro de equipamentos e o cálculo são os dois pontos mais afetados.
- **Sistemas isolados.** O modelo prevê o tipo de sistema elétrico, mas ainda não há definição sobre validar ou não essa informação contra alguma base oficial.
- **Validação do modelo.** O critério de aceite final do TCC é reproduzir os 767 kg da FACOMP a partir dos mesmos dados de entrada. Isso é um teste de validação do trabalho, não uma funcionalidade da plataforma.
