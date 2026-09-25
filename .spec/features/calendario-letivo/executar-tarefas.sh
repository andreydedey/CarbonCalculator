#!/usr/bin/env bash
# executar-tarefas.sh — gerado por `onp-spec plano calendario-letivo` em 2026-09-24 23:52
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
# resumo do que está rolando, a qualquer momento: onp-spec resumo calendario-letivo
set -u
set -o pipefail

RUN_ID='CarbonCalculatorTCC-calendario-letivo-mug6s1zr'
FEATURE='calendario-letivo'
BASE_BRANCH='spec/calendario-letivo'
ENGINE='.claude/skills/onp-spec-driven/scripts/onp-spec.mjs'
CLAUDE_FLAGS=(--permission-mode acceptEdits --allowedTools 'Bash(git add:*),Bash(git commit:*),Bash(git status:*),Bash(git diff:*),Bash(git log:*),Bash(node:*)')
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
  git ls-files --error-unmatch -- '.spec/features/calendario-letivo/spec.md' >/dev/null 2>&1 || falhar "spec.md não está commitada — os worktrees das faixas precisam dela no git"
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
  LOG_DIR="$(dirname "$TOPLEVEL")/onp-worktrees/CarbonCalculatorTCC-calendario-letivo-logs"
  WT_BASE="$(dirname "$TOPLEVEL")/onp-worktrees/CarbonCalculatorTCC-calendario-letivo"
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
    amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --faixa $1"
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

# ── sequencial T-014 (ordem do tasks.md) ──
executar_seq_T_014() {
  info 'sequencial T-014 — Migrations e entidades JPA'
  if rodar_tarefa seq 'T-014' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-014 — "Migrations e entidades JPA"
  critérios/refs: AC-051 (Criação de período com datas válidas), AC-066 (RLS isola períodos por instituição)
  arquivos permitidos (e seus testes): server/src/main/resources/db/migration/V15__create_academic_period_tables.sql, server/src/main/java/com/example/carboncalculator/entities/AcademicPeriod.java, server/src/main/java/com/example/carboncalculator/entities/AcademicPeriodHoliday.java, server/src/main/java/com/example/carboncalculator/entities/LaboratorySchedule.java
  mensagem de commit: "T-014 calendario-letivo: Migrations e entidades JPA"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-014 calendario-letivo: Migrations e entidades JPA (auto-commit do plano)'
    fi
    marcar_concluidas T-014
    verde "✔ T-014 concluída"
    return 0
  fi
  vermelho "✘ T-014 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-014"
  FALHAS="$FALHAS T-014"
  return 1
}

# ── sequencial T-015 (ordem do tasks.md) ──
executar_seq_T_015() {
  info 'sequencial T-015 — Repositories e DTOs'
  if rodar_tarefa seq 'T-015' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-015 — "Repositories e DTOs"
  critérios/refs: AC-054 (Listagem paginada de períodos)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/repositories/AcademicPeriodRepository.java, server/src/main/java/com/example/carboncalculator/repositories/AcademicPeriodHolidayRepository.java, server/src/main/java/com/example/carboncalculator/repositories/LaboratoryScheduleRepository.java, server/src/main/java/com/example/carboncalculator/dto/AcademicPeriodDTO.java, server/src/main/java/com/example/carboncalculator/dto/HolidayDTO.java, server/src/main/java/com/example/carboncalculator/dto/ScheduleBlockDTO.java, server/src/main/java/com/example/carboncalculator/dto/CreateAcademicPeriodRequest.java, server/src/main/java/com/example/carboncalculator/dto/UpdateAcademicPeriodRequest.java, server/src/main/java/com/example/carboncalculator/dto/ReplaceHolidaysRequest.java, server/src/main/java/com/example/carboncalculator/dto/ReplaceScheduleRequest.java, server/src/main/java/com/example/carboncalculator/dto/CopyPeriodRequest.java, server/src/main/java/com/example/carboncalculator/dto/PeriodSummaryDTO.java
  mensagem de commit: "T-015 calendario-letivo: Repositories e DTOs"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-015 calendario-letivo: Repositories e DTOs (auto-commit do plano)'
    fi
    marcar_concluidas T-015
    verde "✔ T-015 concluída"
    return 0
  fi
  vermelho "✘ T-015 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-015"
  FALHAS="$FALHAS T-015"
  return 1
}

# ── sequencial T-016 (ordem do tasks.md) ──
executar_seq_T_016() {
  info 'sequencial T-016 — AcademicPeriodService com validações'
  if rodar_tarefa seq 'T-016' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-016 — "AcademicPeriodService com validações"
  critérios/refs: AC-051 (Criação de período com datas válidas), AC-052 (Rejeição de período com data final anterior à inicial), AC-053 (Rejeição de período sobreposto), AC-055 (Edição de período), AC-056 (Exclusão de período com cascade), AC-057 (Substituição da lista de feriados), AC-058 (Rejeição de feriado fora do intervalo), AC-065 (Cópia de período com feriados e grades)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/services/AcademicPeriodService.java, server/src/main/java/com/example/carboncalculator/exceptions/PeriodOverlapException.java, server/src/main/java/com/example/carboncalculator/exceptions/PeriodNotFoundException.java, server/src/main/java/com/example/carboncalculator/exceptions/HolidayOutOfRangeException.java
  mensagem de commit: "T-016 calendario-letivo: AcademicPeriodService com validações"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-016 calendario-letivo: AcademicPeriodService com validações (auto-commit do plano)'
    fi
    marcar_concluidas T-016
    verde "✔ T-016 concluída"
    return 0
  fi
  vermelho "✘ T-016 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-016"
  FALHAS="$FALHAS T-016"
  return 1
}

# ── sequencial T-017 (ordem do tasks.md) ──
executar_seq_T_017() {
  info 'sequencial T-017 — LaboratoryScheduleService com validações'
  if rodar_tarefa seq 'T-017' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-017 — "LaboratoryScheduleService com validações"
  critérios/refs: AC-059 (Substituição da grade de ocupação), AC-060 (Rejeição de bloco com horário inválido), AC-061 (Rejeição de blocos sobrepostos no mesmo dia)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/services/LaboratoryScheduleService.java, server/src/main/java/com/example/carboncalculator/exceptions/ScheduleBlockOverlapException.java
  mensagem de commit: "T-017 calendario-letivo: LaboratoryScheduleService com validações"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-017 calendario-letivo: LaboratoryScheduleService com validações (auto-commit do plano)'
    fi
    marcar_concluidas T-017
    verde "✔ T-017 concluída"
    return 0
  fi
  vermelho "✘ T-017 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-017"
  FALHAS="$FALHAS T-017"
  return 1
}

# ── sequencial T-018 (ordem do tasks.md) ──
executar_seq_T_018() {
  info 'sequencial T-018 — PeriodSummaryService (cálculos derivados)'
  if rodar_tarefa seq 'T-018' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-018 — "PeriodSummaryService (cálculos derivados)"
  critérios/refs: AC-062 (Cálculo de dias letivos por mês), AC-063 (Cálculo de horas de uso por laboratório por mês), AC-064 (Laboratório sem grade retorna zero horas)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/services/PeriodSummaryService.java
  mensagem de commit: "T-018 calendario-letivo: PeriodSummaryService (cálculos derivados)"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-018 calendario-letivo: PeriodSummaryService (cálculos derivados) (auto-commit do plano)'
    fi
    marcar_concluidas T-018
    verde "✔ T-018 concluída"
    return 0
  fi
  vermelho "✘ T-018 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-018"
  FALHAS="$FALHAS T-018"
  return 1
}

# ── sequencial T-019 (ordem do tasks.md) ──
executar_seq_T_019() {
  info 'sequencial T-019 — Controllers REST'
  if rodar_tarefa seq 'T-019' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-019 — "Controllers REST"
  critérios/refs: AC-051 (Criação de período com datas válidas), AC-054 (Listagem paginada de períodos), AC-057 (Substituição da lista de feriados), AC-059 (Substituição da grade de ocupação), AC-062 (Cálculo de dias letivos por mês), AC-065 (Cópia de período com feriados e grades)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/controllers/AcademicPeriodController.java, server/src/main/java/com/example/carboncalculator/controllers/LaboratoryScheduleController.java
  mensagem de commit: "T-019 calendario-letivo: Controllers REST"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-019 calendario-letivo: Controllers REST (auto-commit do plano)'
    fi
    marcar_concluidas T-019
    verde "✔ T-019 concluída"
    return 0
  fi
  vermelho "✘ T-019 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-019"
  FALHAS="$FALHAS T-019"
  return 1
}

# ── sequencial T-020 (ordem do tasks.md) ──
executar_seq_T_020() {
  info 'sequencial T-020 — GlobalExceptionHandler — novas exceções'
  if rodar_tarefa seq 'T-020' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-020 — "GlobalExceptionHandler — novas exceções"
  critérios/refs: AC-052 (Rejeição de período com data final anterior à inicial), AC-053 (Rejeição de período sobreposto), AC-058 (Rejeição de feriado fora do intervalo), AC-060 (Rejeição de bloco com horário inválido), AC-061 (Rejeição de blocos sobrepostos no mesmo dia)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/exceptions/GlobalExceptionHandler.java
  mensagem de commit: "T-020 calendario-letivo: GlobalExceptionHandler — novas exceções"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' low >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-020 calendario-letivo: GlobalExceptionHandler — novas exceções (auto-commit do plano)'
    fi
    marcar_concluidas T-020
    verde "✔ T-020 concluída"
    return 0
  fi
  vermelho "✘ T-020 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-020"
  FALHAS="$FALHAS T-020"
  return 1
}

# ── sequencial T-021 (ordem do tasks.md) ──
executar_seq_T_021() {
  info 'sequencial T-021 — Seed data para calendário'
  if rodar_tarefa seq 'T-021' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-021 — "Seed data para calendário"
  critérios/refs: US-018, US-019, US-020
  arquivos permitidos (e seus testes): server/src/main/resources/db/migration/afterMigrate.sql
  mensagem de commit: "T-021 calendario-letivo: Seed data para calendário"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' low >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-021 calendario-letivo: Seed data para calendário (auto-commit do plano)'
    fi
    marcar_concluidas T-021
    verde "✔ T-021 concluída"
    return 0
  fi
  vermelho "✘ T-021 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-021"
  FALHAS="$FALHAS T-021"
  return 1
}

# ── sequencial T-022 (ordem do tasks.md) ──
executar_seq_T_022() {
  info 'sequencial T-022 — Frontend: API client e tipos'
  if rodar_tarefa seq 'T-022' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-022 — "Frontend: API client e tipos"
  critérios/refs: US-018, US-019, US-020, US-021, US-022
  arquivos permitidos (e seus testes): client/src/lib/api/academic-periods.ts, client/src/lib/schemas/academicPeriodSchema.ts
  mensagem de commit: "T-022 calendario-letivo: Frontend: API client e tipos"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' low >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-022 calendario-letivo: Frontend: API client e tipos (auto-commit do plano)'
    fi
    marcar_concluidas T-022
    verde "✔ T-022 concluída"
    return 0
  fi
  vermelho "✘ T-022 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-022"
  FALHAS="$FALHAS T-022"
  return 1
}

# ── sequencial T-023 (ordem do tasks.md) ──
executar_seq_T_023() {
  info 'sequencial T-023 — Frontend: listagem e CRUD de períodos'
  if rodar_tarefa seq 'T-023' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-023 — "Frontend: listagem e CRUD de períodos"
  critérios/refs: AC-051 (Criação de período com datas válidas), AC-054 (Listagem paginada de períodos), AC-055 (Edição de período), AC-056 (Exclusão de período com cascade)
  arquivos permitidos (e seus testes): client/src/pages/academic-periods/AcademicPeriodsPage.tsx, client/src/components/academic-periods/AcademicPeriodCard.tsx, client/src/components/academic-periods/AcademicPeriodForm.tsx
  mensagem de commit: "T-023 calendario-letivo: Frontend: listagem e CRUD de períodos"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-023 calendario-letivo: Frontend: listagem e CRUD de períodos (auto-commit do plano)'
    fi
    marcar_concluidas T-023
    verde "✔ T-023 concluída"
    return 0
  fi
  vermelho "✘ T-023 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-023"
  FALHAS="$FALHAS T-023"
  return 1
}

# ── sequencial T-024 (ordem do tasks.md) ──
executar_seq_T_024() {
  info 'sequencial T-024 — Frontend: detalhe do período (feriados + grades + resumo)'
  if rodar_tarefa seq 'T-024' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-024 — "Frontend: detalhe do período (feriados + grades + resumo)"
  critérios/refs: AC-057 (Substituição da lista de feriados), AC-059 (Substituição da grade de ocupação), AC-062 (Cálculo de dias letivos por mês), AC-063 (Cálculo de horas de uso por laboratório por mês)
  arquivos permitidos (e seus testes): client/src/pages/academic-periods/AcademicPeriodDetailPage.tsx, client/src/components/academic-periods/HolidayEditor.tsx, client/src/components/academic-periods/ScheduleGrid.tsx, client/src/components/academic-periods/PeriodSummary.tsx
  mensagem de commit: "T-024 calendario-letivo: Frontend: detalhe do período (feriados + grades + resumo)"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-024 calendario-letivo: Frontend: detalhe do período (feriados + grades + resumo) (auto-commit do plano)'
    fi
    marcar_concluidas T-024
    verde "✔ T-024 concluída"
    return 0
  fi
  vermelho "✘ T-024 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-024"
  FALHAS="$FALHAS T-024"
  return 1
}

# ── sequencial T-025 (ordem do tasks.md) ──
executar_seq_T_025() {
  info 'sequencial T-025 — Frontend: sidebar, rotas e cópia de período'
  if rodar_tarefa seq 'T-025' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-025 — "Frontend: sidebar, rotas e cópia de período"
  critérios/refs: AC-065 (Cópia de período com feriados e grades)
  arquivos permitidos (e seus testes): client/src/components/layout/AppLayout.tsx, client/src/App.tsx, client/src/components/academic-periods/CopyPeriodDialog.tsx
  mensagem de commit: "T-025 calendario-letivo: Frontend: sidebar, rotas e cópia de período"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' low >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-025 calendario-letivo: Frontend: sidebar, rotas e cópia de período (auto-commit do plano)'
    fi
    marcar_concluidas T-025
    verde "✔ T-025 concluída"
    return 0
  fi
  vermelho "✘ T-025 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-025"
  FALHAS="$FALHAS T-025"
  return 1
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
      amarelo "  para o veredito: bash .spec/features/calendario-letivo/executar-tarefas.sh --gate"
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
  executar_seq_T_014 || true
  executar_seq_T_015 || true
  executar_seq_T_016 || true
  executar_seq_T_017 || true
  executar_seq_T_018 || true
  executar_seq_T_019 || true
  executar_seq_T_020 || true
  executar_seq_T_021 || true
  executar_seq_T_022 || true
  executar_seq_T_023 || true
  executar_seq_T_024 || true
  executar_seq_T_025 || true
  encerrar tudo
}

listar() {
  echo "execução: $RUN_ID (feature $FEATURE, branch $BASE_BRANCH)"
  echo "  seq       T-014 (sequencial)"
  echo "  seq       T-015 (sequencial)"
  echo "  seq       T-016 (sequencial)"
  echo "  seq       T-017 (sequencial)"
  echo "  seq       T-018 (sequencial)"
  echo "  seq       T-019 (sequencial)"
  echo "  seq       T-020 (sequencial)"
  echo "  seq       T-021 (sequencial)"
  echo "  seq       T-022 (sequencial)"
  echo "  seq       T-023 (sequencial)"
  echo "  seq       T-024 (sequencial)"
  echo "  seq       T-025 (sequencial)"
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
      *) falhar "faixa desconhecida: '$ALVO' — veja as disponíveis com --listar" ;;
    esac ;;
  seq)
    case "$ALVO" in
      T-014) evento --tipo inicio --escopo "seq:T-014"; iniciar_resumos; executar_seq_T_014 || true; encerrar "seq:T-014" ;;
      T-015) evento --tipo inicio --escopo "seq:T-015"; iniciar_resumos; executar_seq_T_015 || true; encerrar "seq:T-015" ;;
      T-016) evento --tipo inicio --escopo "seq:T-016"; iniciar_resumos; executar_seq_T_016 || true; encerrar "seq:T-016" ;;
      T-017) evento --tipo inicio --escopo "seq:T-017"; iniciar_resumos; executar_seq_T_017 || true; encerrar "seq:T-017" ;;
      T-018) evento --tipo inicio --escopo "seq:T-018"; iniciar_resumos; executar_seq_T_018 || true; encerrar "seq:T-018" ;;
      T-019) evento --tipo inicio --escopo "seq:T-019"; iniciar_resumos; executar_seq_T_019 || true; encerrar "seq:T-019" ;;
      T-020) evento --tipo inicio --escopo "seq:T-020"; iniciar_resumos; executar_seq_T_020 || true; encerrar "seq:T-020" ;;
      T-021) evento --tipo inicio --escopo "seq:T-021"; iniciar_resumos; executar_seq_T_021 || true; encerrar "seq:T-021" ;;
      T-022) evento --tipo inicio --escopo "seq:T-022"; iniciar_resumos; executar_seq_T_022 || true; encerrar "seq:T-022" ;;
      T-023) evento --tipo inicio --escopo "seq:T-023"; iniciar_resumos; executar_seq_T_023 || true; encerrar "seq:T-023" ;;
      T-024) evento --tipo inicio --escopo "seq:T-024"; iniciar_resumos; executar_seq_T_024 || true; encerrar "seq:T-024" ;;
      T-025) evento --tipo inicio --escopo "seq:T-025"; iniciar_resumos; executar_seq_T_025 || true; encerrar "seq:T-025" ;;
      *) falhar "tarefa sequencial desconhecida: '$ALVO' — veja as disponíveis com --listar" ;;
    esac ;;
esac
