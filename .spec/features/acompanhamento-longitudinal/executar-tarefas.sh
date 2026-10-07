#!/usr/bin/env bash
# executar-tarefas.sh — gerado por `onp-spec plano acompanhamento-longitudinal` em 2026-10-01 19:52
# NÃO edite à mão: mudou tasks.md ou a config, regenere o plano.
#
# uso:
#   bash executar-tarefas.sh                  tudo (ondas → sequenciais → gate)
#   bash executar-tarefas.sh --faixa <id>     reexecuta UMA faixa (+ merge + gate)
#   bash executar-tarefas.sh --seq <T-xxx>    reexecuta UMA tarefa sequencial
#   bash executar-tarefas.sh --gate           só o gate (verify + audit)
#   bash executar-tarefas.sh --listar         mostra faixas, tarefas e estados
#   (acrescente --sem-gate para não rodar o gate ao final)
#
# resumo do que está rolando, a qualquer momento: onp-spec resumo acompanhamento-longitudinal
set -u
set -o pipefail

RUN_ID='CarbonCalculatorTCC-acompanhamento-longitudinal-mupyb52k'
FEATURE='acompanhamento-longitudinal'
BASE_BRANCH='spec/acompanhamento-longitudinal'
ENGINE='.claude/skills/onp-spec-driven/scripts/onp-spec.mjs'
CLAUDE_FLAGS=(--permission-mode acceptEdits --allowedTools 'Bash(git add:*),Bash(git commit:*),Bash(git status:*),Bash(git diff:*),Bash(git log:*),Bash(bash:*)')
STREAM_FLAGS=(--output-format stream-json --verbose)
FALHAS=""
COM_GATE=1
RESUMO_MODEL='claude-haiku-4-5'
RESUMO_PID=""

verde()    { printf '\033[32m%s\033[0m\n' "$*"; }
vermelho() { printf '\033[31m%s\033[0m\n' "$*"; }
amarelo()  { printf '\033[33m%s\033[0m\n' "$*"; }
info()     { printf '· %s\n' "$*"; }
falhar()   { vermelho "✘ $*"; exit 1; }

# eventos vão para o ledger GLOBAL (~/.onp-spec/painel/ledger.jsonl):
# um arquivo para todos os projetos, é o que o onp-spec resumo lê
evento() { node "$ENGINE" evento --run "$RUN_ID" "$@" >/dev/null 2>&1 || true; }

# ── ambiente (todos os modos passam por aqui) ────────────────────────
preparar_ambiente() {
  command -v git >/dev/null 2>&1 || falhar "git não encontrado"
  command -v node >/dev/null 2>&1 || falhar "node não encontrado"
  command -v claude >/dev/null 2>&1 || falhar "Claude Code CLI (claude) não encontrado — instale-o ou siga o modo manual em plano-execucao.md"
  TOPLEVEL=$(git rev-parse --show-toplevel 2>/dev/null) || falhar "fora de um repositório git"
  cd "$TOPLEVEL" || exit 1
  # artefatos recém-gerados pelo `onp-spec plano` são sujeira esperada:
  # se forem a ÚNICA sujeira, o script mesmo commita; qualquer outra, aborta
  if [ -n "$(git status --porcelain)" ]; then
    if [ -z "$(git status --porcelain | grep -v -e 'plano-execucao\.' -e 'plano\.json' -e 'executar-tarefas\.sh')" ]; then
      git add -A
      git commit -q -m "plano de execução: $FEATURE (artefatos gerados)"
      info "artefatos do plano commitados"
    else
      falhar "árvore suja além dos artefatos do plano — commite ou faça git stash antes (os worktrees partem do último commit)"
    fi
  fi
  git ls-files --error-unmatch -- '.spec/features/acompanhamento-longitudinal/spec.md' >/dev/null 2>&1 || falhar "spec.md não está commitada — os worktrees das faixas precisam dela no git"
  ATUAL=$(git rev-parse --abbrev-ref HEAD)
  [ "$ATUAL" != "HEAD" ] || falhar "HEAD destacado — troque para uma branch"
  if [ "$ATUAL" != "$BASE_BRANCH" ]; then
    if git show-ref --verify --quiet "refs/heads/$BASE_BRANCH"; then
      git checkout -q "$BASE_BRANCH" || falhar "não consegui trocar para $BASE_BRANCH"
    else
      git checkout -q -b "$BASE_BRANCH" || falhar "não consegui criar $BASE_BRANCH"
    fi
    info "branch de trabalho: $BASE_BRANCH (a partir de $ATUAL)"
  fi
  git worktree prune
  LOG_DIR="$(dirname "$TOPLEVEL")/onp-worktrees/CarbonCalculatorTCC-acompanhamento-longitudinal-logs"
  WT_BASE="$(dirname "$TOPLEVEL")/onp-worktrees/CarbonCalculatorTCC-acompanhamento-longitudinal"
  STREAMS_DIR="${ONP_SPEC_HOME:-$HOME/.onp-spec}/painel/streams/$RUN_ID"
  mkdir -p "$LOG_DIR" "$STREAMS_DIR"
}

# worktree limpo mesmo depois de uma tentativa que falhou
preparar_worktree() { # $1=faixa $2=branch $3=worktree
  git worktree prune
  if [ -e "$3" ]; then git worktree remove --force "$3" >/dev/null 2>&1; rm -rf "$3"; fi
  if git show-ref --verify --quiet "refs/heads/$2"; then git branch -D "$2" >/dev/null 2>&1; fi
  git worktree add "$3" -b "$2" >/dev/null 2>&1 || { vermelho "✘ não consegui criar o worktree de $1 em $3"; return 1; }
}

tentativa() { # $1=faixa — conta reexecuções (vai para o ledger)
  local arq="$LOG_DIR/.tentativa-$1"
  local n=1
  [ -f "$arq" ] && n=$(( $(cat "$arq") + 1 ))
  printf "%s" "$n" > "$arq"
  printf "%s" "$n"
}

# uma tarefa = uma sessão claude headless com contexto limpo.
# o JSONL da sessão vira o stream da tarefa no ledger
rodar_tarefa() { # $1=escopo(faixa|seq) $2=T-xxx $3=prompt $4=modelo $5=esforço
  local chave="$1--$2"
  local stream="$STREAMS_DIR/$chave.jsonl"
  evento --tipo tarefa --tarefa "$2" --faixa "$1" --estado executando --stream "$chave"
  info "$2 — claude -p ($4 · $5) · stream: $chave"
  if claude -p "$3" --model "$4" --effort "$5" "${STREAM_FLAGS[@]}" "${CLAUDE_FLAGS[@]}" > "$stream" 2>>"$LOG_DIR/$1.log"; then
    evento --tipo tarefa --tarefa "$2" --faixa "$1" --estado concluida --stream "$chave"
    node "$ENGINE" stream-resumo "$RUN_ID" "$chave" 2>/dev/null || true
    return 0
  fi
  evento --tipo tarefa --tarefa "$2" --faixa "$1" --estado falhou --stream "$chave"
  node "$ENGINE" stream-resumo "$RUN_ID" "$chave" 2>/dev/null || true
  return 1
}

mesclar_faixa() { # $1=faixa $2=branch $3=worktree $4=exit-da-faixa
  if [ "$4" -ne 0 ]; then
    evento --tipo faixa --faixa "$1" --estado falhou
    vermelho "✘ $1 falhou (log: $LOG_DIR/$1.log) — worktree mantido para inspeção: $3"
    amarelo "  reexecute só ela: bash .spec/features/acompanhamento-longitudinal/executar-tarefas.sh --faixa $1"
    FALHAS="$FALHAS $1"; return 1
  fi
  evento --tipo faixa --faixa "$1" --estado mesclando
  if git merge --no-ff "$2" -m "merge $1 ($FEATURE)"; then
    git worktree remove --force "$3" >/dev/null 2>&1
    git branch -d "$2" >/dev/null 2>&1
    evento --tipo faixa --faixa "$1" --estado mesclada
    verde "✔ $1 mesclada em $BASE_BRANCH"
  else
    git merge --abort >/dev/null 2>&1
    evento --tipo faixa --faixa "$1" --estado conflito
    vermelho "✘ conflito ao mesclar $1 — resolva na mão: git merge $2 (worktree mantido: $3)"
    FALHAS="$FALHAS $1"; return 1
  fi
}

marcar_concluidas() { # $@=T-xxx
  for t in "$@"; do node "$ENGINE" tarefa "$FEATURE" "$t" concluida >/dev/null || true; done
}

# ── resumo geral de andamento: 1/min enquanto a execução roda ─────────
# escrito por IA (claude -p, sem ferramentas) com fallback do motor; vai
# para o terminal e para o ledger — o agente repassa o texto no chat.
gerar_resumo() {
  local ctx ia
  ctx=$(node "$ENGINE" resumo "$FEATURE" --contexto 2>/dev/null) || ctx=""
  [ -n "$ctx" ] || return 0
  ia=$(claude -p "Você narra, para o dono do produto, uma execução de tarefas de código em andamento. Estado mecânico:

$ctx

Escreva o RESUMO GERAL DE ANDAMENTO: um parágrafo único de 2 a 4 frases, em português simples, dizendo o que está acontecendo agora, o que já terminou, o que falhou e se o usuário precisa agir. Sem markdown, sem listas." --model "$RESUMO_MODEL" 2>/dev/null)
  if [ -n "$ia" ]; then
    node "$ENGINE" resumo "$FEATURE" --gravar --origem ia --texto "$ia" >/dev/null 2>&1 || true
    printf '\n📣 resumo (IA): %s\n' "$ia"
  else
    node "$ENGINE" resumo "$FEATURE" --gravar >/dev/null 2>&1 || true
    printf '\n📣 resumo: %s\n' "$(node "$ENGINE" resumo "$FEATURE" 2>/dev/null)"
  fi
}

# mata o loop E o sleep filho — senão o sleep herda o stdout e quem chamou
# o script via pipe fica esperando EOF por até 60s depois do exit
parar_resumos() {
  [ -n "$RESUMO_PID" ] || return 0
  command -v pkill >/dev/null 2>&1 && pkill -P "$RESUMO_PID" 2>/dev/null
  kill "$RESUMO_PID" 2>/dev/null
  RESUMO_PID=""
}

iniciar_resumos() {
  ( while :; do sleep 60; gerar_resumo; done ) &
  RESUMO_PID=$!
  # ao sair: para o loop e grava um último resumo (o estado final, do motor)
  trap 'parar_resumos; node "$ENGINE" resumo "$FEATURE" --gravar >/dev/null 2>&1 || true' EXIT
}

# ── faixa-1: T-051 ──
executar_faixa_1() {
  local WT="$WT_BASE-faixa-1"
  preparar_worktree 'faixa-1' 'spec/acompanhamento-longitudinal-faixa-1' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-1' --estado executando --tentativa "$(tentativa 'faixa-1')"
  : > "$LOG_DIR/faixa-1.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-1' 'T-051' 'Você executa UMA tarefa da feature "acompanhamento-longitudinal" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acompanhamento-longitudinal/spec.md, .spec/features/acompanhamento-longitudinal/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-051 — "Migration V19: tabela emission_snapshot"
  critérios/refs: AC-103 (Agregação mensal soma corretamente dias do mesmo mês), AC-112 (Cron cria snapshot para dia dentro de período ativo com fator disponível)
  arquivos permitidos (e seus testes): server/src/main/resources/db/migration/V19__create_emission_snapshot_table.sql
  mensagem de commit: "T-051 acompanhamento-longitudinal: Migration V19: tabela emission_snapshot"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' low
  ) >> "$LOG_DIR/faixa-1.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-1' 'spec/acompanhamento-longitudinal-faixa-1' "$WT" "$st" || return 1
  marcar_concluidas T-051
  return 0
}

# ── faixa-2: T-052 ──
executar_faixa_2() {
  local WT="$WT_BASE-faixa-2"
  preparar_worktree 'faixa-2' 'spec/acompanhamento-longitudinal-faixa-2' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-2' --estado executando --tentativa "$(tentativa 'faixa-2')"
  : > "$LOG_DIR/faixa-2.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-2' 'T-052' 'Você executa UMA tarefa da feature "acompanhamento-longitudinal" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acompanhamento-longitudinal/spec.md, .spec/features/acompanhamento-longitudinal/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-052 — "Entidade, repositório e DTO de EmissionSnapshot"
  critérios/refs: AC-103 (Agregação mensal soma corretamente dias do mesmo mês), AC-104 (Agregação por período letivo soma todos os dias do período), AC-105 (Agregação semanal agrupa por semana ISO), AC-106 (Granularidade diária retorna um registro por snapshot_date), AC-107 (variationPct calculado em relação ao registro imediatamente anterior), AC-108 (Primeiro registro da série tem variationPct null), AC-109 (RLS: snapshots de outra instituição não são retornados), AC-110 (Filtro startDate/endDate exclui registros fora do intervalo), AC-111 (RESEARCHER consegue consultar snapshots), AC-112 (Cron cria snapshot para dia dentro de período ativo com fator disponível)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/entities/EmissionSnapshot.java, server/src/main/java/com/example/carboncalculator/repositories/EmissionSnapshotRepository.java, server/src/main/java/com/example/carboncalculator/dto/SnapshotAggregateDTO.java
  mensagem de commit: "T-052 acompanhamento-longitudinal: Entidade, repositório e DTO de EmissionSnapshot"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' low
  ) >> "$LOG_DIR/faixa-2.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-2' 'spec/acompanhamento-longitudinal-faixa-2' "$WT" "$st" || return 1
  marcar_concluidas T-052
  return 0
}

# ── faixa-3: T-053 ──
executar_faixa_3() {
  local WT="$WT_BASE-faixa-3"
  preparar_worktree 'faixa-3' 'spec/acompanhamento-longitudinal-faixa-3' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-3' --estado executando --tentativa "$(tentativa 'faixa-3')"
  : > "$LOG_DIR/faixa-3.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-3' 'T-053' 'Você executa UMA tarefa da feature "acompanhamento-longitudinal" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acompanhamento-longitudinal/spec.md, .spec/features/acompanhamento-longitudinal/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-053 — "EmissionSnapshotCronService: captura diária automática"
  critérios/refs: AC-112 (Cron cria snapshot para dia dentro de período ativo com fator disponível), AC-113 (Cron é idempotente — execução duplicada não cria novo snapshot), AC-114 (Cron não cria snapshot para dia fora de período letivo), AC-115 (Cron não cria snapshot quando fator SIN está ausente), AC-116 (Dia feriado gera snapshot com emissão zero), AC-117 (Lab sem schedule para o dayOfWeek do dia não contribui para emissão)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/services/EmissionSnapshotCronService.java
  mensagem de commit: "T-053 acompanhamento-longitudinal: EmissionSnapshotCronService: captura diária automática"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high
  ) >> "$LOG_DIR/faixa-3.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-3' 'spec/acompanhamento-longitudinal-faixa-3' "$WT" "$st" || return 1
  marcar_concluidas T-053
  return 0
}

# ── faixa-4: T-054 ──
executar_faixa_4() {
  local WT="$WT_BASE-faixa-4"
  preparar_worktree 'faixa-4' 'spec/acompanhamento-longitudinal-faixa-4' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-4' --estado executando --tentativa "$(tentativa 'faixa-4')"
  : > "$LOG_DIR/faixa-4.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-4' 'T-054' 'Você executa UMA tarefa da feature "acompanhamento-longitudinal" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acompanhamento-longitudinal/spec.md, .spec/features/acompanhamento-longitudinal/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-054 — "EmissionSnapshotQueryService: agregação por granularidade"
  critérios/refs: AC-103 (Agregação mensal soma corretamente dias do mesmo mês), AC-104 (Agregação por período letivo soma todos os dias do período), AC-105 (Agregação semanal agrupa por semana ISO), AC-106 (Granularidade diária retorna um registro por snapshot_date), AC-107 (variationPct calculado em relação ao registro imediatamente anterior), AC-108 (Primeiro registro da série tem variationPct null), AC-109 (RLS: snapshots de outra instituição não são retornados), AC-110 (Filtro startDate/endDate exclui registros fora do intervalo)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/services/EmissionSnapshotQueryService.java
  mensagem de commit: "T-054 acompanhamento-longitudinal: EmissionSnapshotQueryService: agregação por granularidade"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high
  ) >> "$LOG_DIR/faixa-4.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-4' 'spec/acompanhamento-longitudinal-faixa-4' "$WT" "$st" || return 1
  marcar_concluidas T-054
  return 0
}

# ── faixa-5: T-055 ──
executar_faixa_5() {
  local WT="$WT_BASE-faixa-5"
  preparar_worktree 'faixa-5' 'spec/acompanhamento-longitudinal-faixa-5' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-5' --estado executando --tentativa "$(tentativa 'faixa-5')"
  : > "$LOG_DIR/faixa-5.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-5' 'T-055' 'Você executa UMA tarefa da feature "acompanhamento-longitudinal" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acompanhamento-longitudinal/spec.md, .spec/features/acompanhamento-longitudinal/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-055 — "EmissionSnapshotController: GET /api/v1/snapshots"
  critérios/refs: AC-103 (Agregação mensal soma corretamente dias do mesmo mês), AC-104 (Agregação por período letivo soma todos os dias do período), AC-105 (Agregação semanal agrupa por semana ISO), AC-106 (Granularidade diária retorna um registro por snapshot_date), AC-107 (variationPct calculado em relação ao registro imediatamente anterior), AC-108 (Primeiro registro da série tem variationPct null), AC-109 (RLS: snapshots de outra instituição não são retornados), AC-110 (Filtro startDate/endDate exclui registros fora do intervalo), AC-111 (RESEARCHER consegue consultar snapshots)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/controllers/EmissionSnapshotController.java
  mensagem de commit: "T-055 acompanhamento-longitudinal: EmissionSnapshotController: GET /api/v1/snapshots"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' low
  ) >> "$LOG_DIR/faixa-5.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-5' 'spec/acompanhamento-longitudinal-faixa-5' "$WT" "$st" || return 1
  marcar_concluidas T-055
  return 0
}

# ── faixa-6: T-056 ──
executar_faixa_6() {
  local WT="$WT_BASE-faixa-6"
  preparar_worktree 'faixa-6' 'spec/acompanhamento-longitudinal-faixa-6' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-6' --estado executando --tentativa "$(tentativa 'faixa-6')"
  : > "$LOG_DIR/faixa-6.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-6' 'T-056' 'Você executa UMA tarefa da feature "acompanhamento-longitudinal" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acompanhamento-longitudinal/spec.md, .spec/features/acompanhamento-longitudinal/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-056 — "Testes de integração: cron e query"
  critérios/refs: AC-103 (Agregação mensal soma corretamente dias do mesmo mês), AC-104 (Agregação por período letivo soma todos os dias do período), AC-105 (Agregação semanal agrupa por semana ISO), AC-106 (Granularidade diária retorna um registro por snapshot_date), AC-107 (variationPct calculado em relação ao registro imediatamente anterior), AC-108 (Primeiro registro da série tem variationPct null), AC-109 (RLS: snapshots de outra instituição não são retornados), AC-110 (Filtro startDate/endDate exclui registros fora do intervalo), AC-111 (RESEARCHER consegue consultar snapshots), AC-112 (Cron cria snapshot para dia dentro de período ativo com fator disponível), AC-113 (Cron é idempotente — execução duplicada não cria novo snapshot), AC-114 (Cron não cria snapshot para dia fora de período letivo), AC-115 (Cron não cria snapshot quando fator SIN está ausente), AC-116 (Dia feriado gera snapshot com emissão zero), AC-117 (Lab sem schedule para o dayOfWeek do dia não contribui para emissão)
  arquivos permitidos (e seus testes): server/src/test/java/com/example/carboncalculator/EmissionSnapshotIntegrationTest.java
  mensagem de commit: "T-056 acompanhamento-longitudinal: Testes de integração: cron e query"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high
  ) >> "$LOG_DIR/faixa-6.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-6' 'spec/acompanhamento-longitudinal-faixa-6' "$WT" "$st" || return 1
  marcar_concluidas T-056
  return 0
}

# ── faixa-7: T-057 ──
executar_faixa_7() {
  local WT="$WT_BASE-faixa-7"
  preparar_worktree 'faixa-7' 'spec/acompanhamento-longitudinal-faixa-7' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-7' --estado executando --tentativa "$(tentativa 'faixa-7')"
  : > "$LOG_DIR/faixa-7.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-7' 'T-057' 'Você executa UMA tarefa da feature "acompanhamento-longitudinal" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acompanhamento-longitudinal/spec.md, .spec/features/acompanhamento-longitudinal/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-057 — "Testes unitários: EmissionSnapshotQueryService"
  critérios/refs: AC-103 (Agregação mensal soma corretamente dias do mesmo mês), AC-107 (variationPct calculado em relação ao registro imediatamente anterior), AC-108 (Primeiro registro da série tem variationPct null)
  arquivos permitidos (e seus testes): server/src/test/java/com/example/carboncalculator/EmissionSnapshotQueryServiceTest.java
  mensagem de commit: "T-057 acompanhamento-longitudinal: Testes unitários: EmissionSnapshotQueryService"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium
  ) >> "$LOG_DIR/faixa-7.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-7' 'spec/acompanhamento-longitudinal-faixa-7' "$WT" "$st" || return 1
  marcar_concluidas T-057
  return 0
}

# ── faixa-8: T-058 ──
executar_faixa_8() {
  local WT="$WT_BASE-faixa-8"
  preparar_worktree 'faixa-8' 'spec/acompanhamento-longitudinal-faixa-8' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-8' --estado executando --tentativa "$(tentativa 'faixa-8')"
  : > "$LOG_DIR/faixa-8.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-8' 'T-058' 'Você executa UMA tarefa da feature "acompanhamento-longitudinal" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acompanhamento-longitudinal/spec.md, .spec/features/acompanhamento-longitudinal/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-058 — "LongitudinalPage: React com toggle, gráfico e tabela"
  critérios/refs: AC-118 (Página exibe 4 KPI cards com valores calculados), AC-119 (Toggle de granularidade atualiza gráfico e tabela), AC-120 (Estado vazio exibe mensagem explicativa), AC-121 (Variação positiva é vermelha, negativa é verde)
  arquivos permitidos (e seus testes): client/src/pages/longitudinal/LongitudinalPage.tsx, client/src/lib/api/snapshots.ts, client/src/lib/schemas/snapshotSchema.ts
  mensagem de commit: "T-058 acompanhamento-longitudinal: LongitudinalPage: React com toggle, gráfico e tabela"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high
  ) >> "$LOG_DIR/faixa-8.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-8' 'spec/acompanhamento-longitudinal-faixa-8' "$WT" "$st" || return 1
  marcar_concluidas T-058
  return 0
}

# ── faixa-9: T-059 ──
executar_faixa_9() {
  local WT="$WT_BASE-faixa-9"
  preparar_worktree 'faixa-9' 'spec/acompanhamento-longitudinal-faixa-9' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-9' --estado executando --tentativa "$(tentativa 'faixa-9')"
  : > "$LOG_DIR/faixa-9.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-9' 'T-059' 'Você executa UMA tarefa da feature "acompanhamento-longitudinal" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acompanhamento-longitudinal/spec.md, .spec/features/acompanhamento-longitudinal/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-059 — "Testes de componente: LongitudinalPage"
  critérios/refs: AC-118 (Página exibe 4 KPI cards com valores calculados), AC-119 (Toggle de granularidade atualiza gráfico e tabela), AC-120 (Estado vazio exibe mensagem explicativa), AC-121 (Variação positiva é vermelha, negativa é verde)
  arquivos permitidos (e seus testes): client/src/pages/longitudinal/LongitudinalPage.test.tsx
  mensagem de commit: "T-059 acompanhamento-longitudinal: Testes de componente: LongitudinalPage"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium
  ) >> "$LOG_DIR/faixa-9.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-9' 'spec/acompanhamento-longitudinal-faixa-9' "$WT" "$st" || return 1
  marcar_concluidas T-059
  return 0
}

# ── gate: quem decide é a máquina ────────────────────────────────────
rodar_gate() {
  echo
  info "gate: verify + audit --ci"
  evento --tipo gate --etapa inicio
  node "$ENGINE" verify "$FEATURE"
  local v=$?
  evento --tipo gate --etapa verify --exit "$v"
  node "$ENGINE" audit --ci
  AUDIT=$?
  evento --tipo gate --etapa audit --exit "$AUDIT"
  # fecha a contabilidade: status das tarefas + prova do verify no git
  if [ -n "$(git status --porcelain -- '.spec')" ]; then
    git add -A -- '.spec'
    git commit -q -m "$FEATURE: status das tarefas + prova do verify (plano)"
    info "status das tarefas e prova do verify commitados"
  fi
  return "$AUDIT"
}

encerrar() { # $1=escopo
  echo
  if [ -n "$FALHAS" ]; then vermelho "faixas/tarefas com falha:$FALHAS"; fi
  # sem gate não existe veredito: NUNCA anunciar alinhamento sem o audit
  if [ "$COM_GATE" -eq 0 ]; then
    evento --tipo fim --exit 1 --escopo "$1"
    if [ -z "$FALHAS" ]; then
      amarelo "○ trabalho de '$1' terminou SEM o gate (--sem-gate) — isto NÃO é prova de nada"
      amarelo "  para o veredito: bash .spec/features/acompanhamento-longitudinal/executar-tarefas.sh --gate"
      exit 0
    fi
    vermelho "e ainda há falhas — conserte e rode o gate"
    exit 1
  fi
  rodar_gate
  local audit=$?
  if [ "$audit" -eq 0 ] && [ -z "$FALHAS" ]; then
    evento --tipo fim --exit 0 --escopo "$1"
    verde "✔ plano concluído — especificação e código alinhados (audit exit 0) na branch $BASE_BRANCH"
    info "próximo passo: revise e leve para a main quando quiser (git merge $BASE_BRANCH)"
    exit 0
  fi
  evento --tipo fim --exit 1 --escopo "$1"
  vermelho "plano terminou com pendências — leia a saída do audit acima e os logs em $LOG_DIR"
  amarelo "dica: reexecute só o que falhou (--faixa <id> / --seq <T-xxx>)"
  exit 1
}

executar_tudo() {
  evento --tipo inicio --escopo tudo
  iniciar_resumos
  info "logs em: $LOG_DIR"
  info "resumo geral de andamento: a cada 1 min aqui no terminal (e via: onp-spec resumo)"
  # onda 1: faixa-1 ∥ faixa-2 ∥ faixa-3
  info "onda 1: faixa-1 ∥ faixa-2 ∥ faixa-3 — janelas limpas em paralelo"
  executar_faixa_1 & PID_FAIXA_1=$!
  executar_faixa_2 & PID_FAIXA_2=$!
  executar_faixa_3 & PID_FAIXA_3=$!
  wait "$PID_FAIXA_1" || true
  wait "$PID_FAIXA_2" || true
  wait "$PID_FAIXA_3" || true
  # onda 2: faixa-4 ∥ faixa-5 ∥ faixa-6
  info "onda 2: faixa-4 ∥ faixa-5 ∥ faixa-6 — janelas limpas em paralelo"
  executar_faixa_4 & PID_FAIXA_4=$!
  executar_faixa_5 & PID_FAIXA_5=$!
  executar_faixa_6 & PID_FAIXA_6=$!
  wait "$PID_FAIXA_4" || true
  wait "$PID_FAIXA_5" || true
  wait "$PID_FAIXA_6" || true
  # onda 3: faixa-7 ∥ faixa-8 ∥ faixa-9
  info "onda 3: faixa-7 ∥ faixa-8 ∥ faixa-9 — janelas limpas em paralelo"
  executar_faixa_7 & PID_FAIXA_7=$!
  executar_faixa_8 & PID_FAIXA_8=$!
  executar_faixa_9 & PID_FAIXA_9=$!
  wait "$PID_FAIXA_7" || true
  wait "$PID_FAIXA_8" || true
  wait "$PID_FAIXA_9" || true
  encerrar tudo
}

listar() {
  echo "execução: $RUN_ID (feature $FEATURE, branch $BASE_BRANCH)"
  echo "  faixa-1  onda 1  T-051"
  echo "  faixa-2  onda 1  T-052"
  echo "  faixa-3  onda 1  T-053"
  echo "  faixa-4  onda 2  T-054"
  echo "  faixa-5  onda 2  T-055"
  echo "  faixa-6  onda 2  T-056"
  echo "  faixa-7  onda 3  T-057"
  echo "  faixa-8  onda 3  T-058"
  echo "  faixa-9  onda 3  T-059"
  echo
  echo "reexecutar uma faixa:    --faixa <id>"
  echo "reexecutar sequencial:   --seq <T-xxx>"
  echo "só o gate:               --gate"
}

MODO="tudo"
ALVO=""
while [ $# -gt 0 ]; do
  case "$1" in
    --listar) MODO="listar" ;;
    --gate) MODO="gate" ;;
    --sem-gate) COM_GATE=0 ;;
    --faixa) MODO="faixa"; ALVO="${2:-}"; shift ;;
    --seq) MODO="seq"; ALVO="${2:-}"; shift ;;
    -h|--help) sed -n "2,14p" "$0"; exit 0 ;;
    *) vermelho "argumento desconhecido: $1"; sed -n "2,14p" "$0"; exit 2 ;;
  esac
  shift
done

if [ "$MODO" = "listar" ]; then listar; exit 0; fi

preparar_ambiente

case "$MODO" in
  tudo) executar_tudo ;;
  gate) COM_GATE=1; iniciar_resumos; encerrar gate ;;
  faixa)
    case "$ALVO" in
      faixa-1) evento --tipo inicio --escopo "faixa:faixa-1"; iniciar_resumos; executar_faixa_1 || true; encerrar "faixa:faixa-1" ;;
      faixa-2) evento --tipo inicio --escopo "faixa:faixa-2"; iniciar_resumos; executar_faixa_2 || true; encerrar "faixa:faixa-2" ;;
      faixa-3) evento --tipo inicio --escopo "faixa:faixa-3"; iniciar_resumos; executar_faixa_3 || true; encerrar "faixa:faixa-3" ;;
      faixa-4) evento --tipo inicio --escopo "faixa:faixa-4"; iniciar_resumos; executar_faixa_4 || true; encerrar "faixa:faixa-4" ;;
      faixa-5) evento --tipo inicio --escopo "faixa:faixa-5"; iniciar_resumos; executar_faixa_5 || true; encerrar "faixa:faixa-5" ;;
      faixa-6) evento --tipo inicio --escopo "faixa:faixa-6"; iniciar_resumos; executar_faixa_6 || true; encerrar "faixa:faixa-6" ;;
      faixa-7) evento --tipo inicio --escopo "faixa:faixa-7"; iniciar_resumos; executar_faixa_7 || true; encerrar "faixa:faixa-7" ;;
      faixa-8) evento --tipo inicio --escopo "faixa:faixa-8"; iniciar_resumos; executar_faixa_8 || true; encerrar "faixa:faixa-8" ;;
      faixa-9) evento --tipo inicio --escopo "faixa:faixa-9"; iniciar_resumos; executar_faixa_9 || true; encerrar "faixa:faixa-9" ;;
      *) falhar "faixa desconhecida: '$ALVO' — veja as disponíveis com --listar" ;;
    esac ;;
  seq)
    case "$ALVO" in
      *) falhar "tarefa sequencial desconhecida: '$ALVO' — veja as disponíveis com --listar" ;;
    esac ;;
esac
