# Spec: Cálculo e apresentação das emissões

> feature: calculo-de-emissoes
> status: rascunho

## Contexto

O sistema já cadastra o parque computacional (modelos, monitores, configurações com quantidades por laboratório) e o calendário letivo (períodos, turnos, feriados, grade de ocupação). Falta o fator de emissão mensal (kgCO₂/kWh) e o motor de cálculo que multiplica consumo × horas × fator para responder "quanto CO₂ este laboratório emite em um semestre". Este é o objetivo central do TCC.

## Histórias

### US-026 — CRUD de fatores de emissão

Como administrador, quero cadastrar os fatores de emissão mensais do SIN (kgCO₂/kWh) publicados pelo MCTI, para que o cálculo use o fator correto de cada mês.

#### AC-072 — Criar fator de emissão válido

- **Dado** que sou administrador e informo ano, mês, valor e fonte
- **Quando** submeto o formulário de criação
- **Então** o fator é salvo e aparece na listagem

#### AC-073 — Rejeitar fator duplicado (mesmo ano+mês)

- **Dado** que já existe um fator para março/2025
- **Quando** tento criar outro fator para março/2025
- **Então** a plataforma recusa com erro 409 indicando duplicidade

#### AC-074 — Rejeitar fator com valor inválido

- **Dado** que informo um valor ≤ 0 ou deixo a fonte vazia
- **Quando** submeto o formulário
- **Então** a plataforma recusa com erro 400 indicando os campos inválidos

#### AC-075 — Listar fatores filtrados por ano

- **Dado** que existem fatores de 2024 e 2025
- **Quando** filtro por ano 2025
- **Então** vejo apenas os fatores de 2025, paginados

#### AC-076 — Atualizar fator existente

- **Dado** que existe um fator para abril/2025
- **Quando** altero seu valor e salvo
- **Então** o novo valor é exibido na listagem

#### AC-077 — Excluir fator de emissão

- **Dado** que existe um fator cadastrado
- **Quando** clico em excluir e confirmo
- **Então** o fator é removido da listagem

#### AC-078 — Apenas administradores gerenciam fatores

- **Dado** que sou pesquisador (não admin)
- **Quando** tento criar, atualizar ou excluir um fator
- **Então** a plataforma recusa com erro 403

### US-027 — Verificar pré-requisitos do cálculo

Como gestor, quero saber se todos os dados necessários estão cadastrados antes de calcular, para não obter resultados incompletos.

#### AC-079 — Todos os pré-requisitos atendidos

- **Dado** que todos os meses do período têm fator de emissão e todos os labs têm equipamentos e grade de ocupação
- **Quando** verifico a prontidão do cálculo
- **Então** o sistema indica que está pronto (ready: true)

#### AC-080 — Meses sem fator de emissão

- **Dado** que o período abrange março a julho e falta o fator de junho
- **Quando** verifico a prontidão
- **Então** o sistema indica não pronto e lista "2025-06" como fator faltante

#### AC-081 — Laboratório sem grade de ocupação

- **Dado** que um laboratório ativo não tem nenhum horário preenchido na grade
- **Quando** verifico a prontidão
- **Então** o sistema lista esse laboratório como "sem ocupação definida"

#### AC-082 — Configuração sem monitor sinalizada como aviso

- **Dado** que uma configuração não tem monitor vinculado
- **Quando** verifico a prontidão
- **Então** o cálculo ainda é marcado como pronto, mas o aviso lista a configuração e os laboratórios afetados

### US-028 — Calcular emissões de um período letivo

Como gestor institucional, quero calcular as emissões de CO₂ de todos os laboratórios em um período letivo, para ter o número que será reportado.

#### AC-083 — Cálculo correto para 1 configuração e 1 mês

- **Dado** um laboratório com 1 configuração (TDP 65W, monitor 21W, quantidade 10) e 1 mês com 80 horas de uso e fator 0.0425
- **Quando** solicito o cálculo
- **Então** a energia é (65+21)×80×10/1000 = 68.8 kWh e a emissão é 68.8×0.0425 = 2.924 kgCO₂

#### AC-084 — Cada mês usa seu próprio fator

- **Dado** um período com março (fator 0.0425) e abril (fator 0.0450)
- **Quando** o cálculo é executado
- **Então** a emissão de março usa 0.0425 e a de abril usa 0.0450 (não uma média)

#### AC-085 — Configuração com GPU inclui gpuTdpWatts

- **Dado** uma configuração com TDP 65W e GPU 75W e monitor 21W
- **Quando** o cálculo é executado
- **Então** o consumo da estação é 65+75+21 = 161W

#### AC-086 — Configuração sem monitor calcula só computador

- **Dado** uma configuração sem monitor vinculado
- **Quando** o cálculo é executado
- **Então** o consumo considera apenas TDP (+ GPU se houver) e o resultado sinaliza que o monitor não está contabilizado

#### AC-087 — Decomposição computador vs. monitor soma igual ao total

- **Dado** o resultado de um laboratório
- **Quando** verifico a decomposição computador vs. monitor
- **Então** a soma de computerEmissionKg + monitorEmissionKg = emissionKg do laboratório

#### AC-088 — Decomposição por modelo de equipamento soma igual ao total do lab

- **Dado** o resultado de um laboratório com múltiplas configurações
- **Quando** verifico a decomposição por modelo
- **Então** a soma das emissões por modelo = emissão total do laboratório

#### AC-089 — Decomposição por sistema operacional soma igual ao total

- **Dado** o resultado com configurações em Windows e Linux
- **Quando** verifico a decomposição por SO
- **Então** a soma das parcelas por SO = emissão total

#### AC-090 — Equivalências calculadas corretamente

- **Dado** uma emissão total de 767.42 kgCO₂
- **Quando** verifico as equivalências
- **Então** carKm = 767.42/0.1667 ≈ 4604 e treesNeeded = 767.42/145.14 ≈ 5.29

#### AC-091 — Feriados descontados do cálculo

- **Dado** um período com feriado em um dia que seria letivo
- **Quando** o cálculo é executado
- **Então** esse dia não conta como dia letivo (as horas de uso do mês refletem a ausência desse dia)

#### AC-092 — Laboratório sem grade tem emissão zero

- **Dado** um laboratório sem grade de ocupação definida
- **Quando** o cálculo é executado
- **Então** a emissão desse laboratório é zero (sem horas de uso)

### US-029 — Visualizar dashboard de emissões

Como membro da coordenação, quero ver o resultado decomposto por laboratório, turno, dia da semana e mês em um dashboard, para identificar onde intervir.

#### AC-093 — Dashboard mostra total e gráfico por mês

- **Dado** que o cálculo de um período está pronto
- **Quando** acesso a página de emissões e seleciono o período
- **Então** vejo o total de emissão, o total de energia e um gráfico de barras por mês

#### AC-094 — Dashboard mostra decomposição por laboratório

- **Dado** que o resultado contém múltiplos laboratórios
- **Quando** acesso o dashboard
- **Então** vejo a emissão de cada laboratório com quantidade de estações e parcela computador/monitor

#### AC-095 — Dashboard mostra equivalências do cotidiano

- **Dado** que o cálculo produziu um total
- **Quando** acesso o dashboard
- **Então** vejo cards com equivalências (km de carro, árvores) que tornam o número compreensível

#### AC-096 — Dashboard mostra rankings de modelos e SOs

- **Dado** que o resultado contém decomposição por modelo, monitor e SO
- **Quando** acesso o dashboard
- **Então** vejo a contribuição de cada modelo de computador, monitor e sistema operacional para o total

#### AC-097 — Dashboard mostra painel de transparência

- **Dado** que o resultado foi calculado
- **Quando** expando o painel de transparência
- **Então** vejo os fatores de emissão usados, a fonte de consumo de cada configuração e os dados de entrada

### US-030 — Exportar resultados em CSV

Como pesquisador, quero exportar os resultados do cálculo em CSV, para usá-los em relatórios ou artigos.

#### AC-098 — Download CSV com dados corretos

- **Dado** que o cálculo de um período está completo
- **Quando** clico em "Exportar CSV"
- **Então** recebo um arquivo CSV com cabeçalho e linhas por laboratório/mês que batem com os dados do dashboard

#### AC-099 — CSV tem Content-Type e nome de arquivo corretos

- **Dado** que solicito a exportação
- **Quando** o download inicia
- **Então** o Content-Type é text/csv e o nome do arquivo contém o nome do período

### US-031 — Isolamento por instituição

Como gestor de uma instituição, quero que o cálculo considere apenas os laboratórios da minha instituição, sem ver dados de outras.

#### AC-100 — Emissões calculadas apenas com labs da instituição

- **Dado** que existem laboratórios de duas instituições distintas
- **Quando** a instituição A solicita o cálculo
- **Então** apenas os laboratórios da instituição A entram no resultado

#### AC-101 — Fatores de emissão são isolados por instituição

- **Dado** que fatores de emissão foram cadastrados para uma instituição
- **Quando** outra instituição lista os fatores
- **Então** ela não vê os fatores da primeira (isolamento via RLS por `institution_id`)

## Fora de escopo

- Emissões de escopo 1 e 3
- Medições físicas de consumo (PRD 04) — usa especificações de fabricante
- Fator de sistemas isolados (PRD 09 completo)
- Persistência/cache do resultado calculado
- Simulação de cenários (PRD 07)
- Comparação entre períodos (PRD 08)
- Exportação em PDF
- Recomendações automáticas de mitigação
- Conversão monetária do consumo

## Suposições

| ID | Suposição | Status | Resolução |
|---|---|---|---|
| ASM-016 | O consumo real de uma estação é adequadamente representado pela soma TDP CPU + TDP GPU + watts do monitor (especificação de fabricante) | confirmada | TDD resolveu: usar especificações até PRD 04 (medições) ser implementado |
| ASM-017 | O PeriodSummaryService já calcula corretamente horas de uso por lab/mês descontando feriados e dias inativos | confirmada | Verificado no código — PeriodSummaryService.getSummary() faz esse cálculo |
| ASM-018 | Todas as instituições usam o fator SIN (sistema interligado nacional) — não há instituição em sistema isolado neste MVP | confirmada | TDD resolveu: suporte a sistemas isolados fica para PRD 09 completo |
| ASM-019 | As equivalências (km de carro = emissão/0.1667, árvores = emissão/145.14) são valores fixos aceitáveis para o MVP | confirmada | TDD resolveu: fixas no V1, baseadas no estudo de referência |

## Perguntas em aberto

| ID | Pergunta | Status | Resposta |
|---|---|---|---|
| Q-007 | Design das telas de emissões e fatores de emissão no Pencil deve ser criado antes do frontend? | respondida | Implementar frontend sem design prévio, seguindo padrões existentes do projeto (mesma estrutura das páginas de equipamentos e calendário) |
