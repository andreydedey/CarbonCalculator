#!/usr/bin/env bash
# executar-tarefas.sh — gerado por `onp-spec plano calendario-letivo` em 2026-09-25 17:32
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

RUN_ID='CarbonCalculatorTCC-calendario-letivo-muh8nw6h'
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

# ── sequencial T-026 (ordem do tasks.md) ──
executar_seq_T_026() {
  info 'sequencial T-026 — Migrations: turnos e novo modelo de schedule'
  if rodar_tarefa seq 'T-026' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-026 — "Migrations: turnos e novo modelo de schedule"
  critérios/refs: AC-067 (Substituição da configuração de turnos), AC-059 (Substituição da grade de ocupação por slots)
  arquivos permitidos (e seus testes): server/src/main/resources/db/migration/V15__create_academic_period_tables.sql
  mensagem de commit: "T-026 calendario-letivo: Migrations: turnos e novo modelo de schedule"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-026 calendario-letivo: Migrations: turnos e novo modelo de schedule (auto-commit do plano)'
    fi
    marcar_concluidas T-026
    verde "✔ T-026 concluída"
    return 0
  fi
  vermelho "✘ T-026 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-026"
  FALHAS="$FALHAS T-026"
  return 1
}

# ── sequencial T-027 (ordem do tasks.md) ──
executar_seq_T_027() {
  info 'sequencial T-027 — Entidades JPA atualizadas'
  if rodar_tarefa seq 'T-027' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-027 — "Entidades JPA atualizadas"
  critérios/refs: AC-067 (Substituição da configuração de turnos), AC-059 (Substituição da grade de ocupação por slots), AC-057 (Substituição da lista de feriados com tipo)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/entities/AcademicPeriod.java, server/src/main/java/com/example/carboncalculator/entities/AcademicPeriodHoliday.java, server/src/main/java/com/example/carboncalculator/entities/AcademicPeriodShift.java, server/src/main/java/com/example/carboncalculator/entities/LaboratorySchedule.java
  mensagem de commit: "T-027 calendario-letivo: Entidades JPA atualizadas"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-027 calendario-letivo: Entidades JPA atualizadas (auto-commit do plano)'
    fi
    marcar_concluidas T-027
    verde "✔ T-027 concluída"
    return 0
  fi
  vermelho "✘ T-027 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-027"
  FALHAS="$FALHAS T-027"
  return 1
}

# ── sequencial T-028 (ordem do tasks.md) ──
executar_seq_T_028() {
  info 'sequencial T-028 — DTOs e schemas atualizados'
  if rodar_tarefa seq 'T-028' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-028 — "DTOs e schemas atualizados"
  critérios/refs: AC-067 (Substituição da configuração de turnos), AC-059 (Substituição da grade de ocupação por slots), AC-057 (Substituição da lista de feriados com tipo)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/dto/ShiftDTO.java, server/src/main/java/com/example/carboncalculator/dto/ReplaceShiftsRequest.java, server/src/main/java/com/example/carboncalculator/dto/AcademicPeriodDTO.java, server/src/main/java/com/example/carboncalculator/dto/HolidayDTO.java, server/src/main/java/com/example/carboncalculator/dto/ReplaceHolidaysRequest.java, server/src/main/java/com/example/carboncalculator/dto/ScheduleEntryDTO.java, server/src/main/java/com/example/carboncalculator/dto/ReplaceScheduleRequest.java, server/src/main/java/com/example/carboncalculator/dto/PeriodSummaryDTO.java
  mensagem de commit: "T-028 calendario-letivo: DTOs e schemas atualizados"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-028 calendario-letivo: DTOs e schemas atualizados (auto-commit do plano)'
    fi
    marcar_concluidas T-028
    verde "✔ T-028 concluída"
    return 0
  fi
  vermelho "✘ T-028 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-028"
  FALHAS="$FALHAS T-028"
  return 1
}

# ── sequencial T-029 (ordem do tasks.md) ──
executar_seq_T_029() {
  info 'sequencial T-029 — ShiftService com validações'
  if rodar_tarefa seq 'T-029' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-029 — "ShiftService com validações"
  critérios/refs: AC-067 (Substituição da configuração de turnos), AC-068 (Rejeição de turno com classesPerDay zero), AC-069 (Rejeição de turno duplicado no mesmo período)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/services/ShiftService.java, server/src/main/java/com/example/carboncalculator/repositories/AcademicPeriodShiftRepository.java
  mensagem de commit: "T-029 calendario-letivo: ShiftService com validações"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-029 calendario-letivo: ShiftService com validações (auto-commit do plano)'
    fi
    marcar_concluidas T-029
    verde "✔ T-029 concluída"
    return 0
  fi
  vermelho "✘ T-029 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-029"
  FALHAS="$FALHAS T-029"
  return 1
}

# ── sequencial T-030 (ordem do tasks.md) ──
executar_seq_T_030() {
  info 'sequencial T-030 — AcademicPeriodService atualizado'
  if rodar_tarefa seq 'T-030' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-030 — "AcademicPeriodService atualizado"
  critérios/refs: AC-051 (Criação de período com datas válidas), AC-052 (Rejeição de período com data final anterior à inicial), AC-053 (Rejeição de período sobreposto), AC-055 (Edição de período), AC-056 (Exclusão de período com cascade), AC-057 (Substituição da lista de feriados com tipo), AC-058 (Rejeição de feriado fora do intervalo), AC-065 (Cópia de período com turnos, feriados e grades)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/services/AcademicPeriodService.java
  mensagem de commit: "T-030 calendario-letivo: AcademicPeriodService atualizado"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-030 calendario-letivo: AcademicPeriodService atualizado (auto-commit do plano)'
    fi
    marcar_concluidas T-030
    verde "✔ T-030 concluída"
    return 0
  fi
  vermelho "✘ T-030 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-030"
  FALHAS="$FALHAS T-030"
  return 1
}

# ── sequencial T-031 (ordem do tasks.md) ──
executar_seq_T_031() {
  info 'sequencial T-031 — LaboratoryScheduleService com validação de slots'
  if rodar_tarefa seq 'T-031' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-031 — "LaboratoryScheduleService com validação de slots"
  critérios/refs: AC-059 (Substituição da grade de ocupação por slots), AC-060 (Rejeição de slot fora do range do turno), AC-061 (Rejeição de dia fora dos activeDays do turno)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/services/LaboratoryScheduleService.java, server/src/main/java/com/example/carboncalculator/repositories/LaboratoryScheduleRepository.java
  mensagem de commit: "T-031 calendario-letivo: LaboratoryScheduleService com validação de slots"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-031 calendario-letivo: LaboratoryScheduleService com validação de slots (auto-commit do plano)'
    fi
    marcar_concluidas T-031
    verde "✔ T-031 concluída"
    return 0
  fi
  vermelho "✘ T-031 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-031"
  FALHAS="$FALHAS T-031"
  return 1
}

# ── sequencial T-032 (ordem do tasks.md) ──
executar_seq_T_032() {
  info 'sequencial T-032 — PeriodSummaryService com cálculo por slots'
  if rodar_tarefa seq 'T-032' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-032 — "PeriodSummaryService com cálculo por slots"
  critérios/refs: AC-062 (Cálculo de dias letivos por mês), AC-063 (Cálculo de horas de uso por slots ocupados), AC-064 (Laboratório sem grade retorna zero horas), AC-070 (Turno desativado não contabiliza no cálculo)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/services/PeriodSummaryService.java
  mensagem de commit: "T-032 calendario-letivo: PeriodSummaryService com cálculo por slots"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-032 calendario-letivo: PeriodSummaryService com cálculo por slots (auto-commit do plano)'
    fi
    marcar_concluidas T-032
    verde "✔ T-032 concluída"
    return 0
  fi
  vermelho "✘ T-032 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-032"
  FALHAS="$FALHAS T-032"
  return 1
}

# ── sequencial T-033 (ordem do tasks.md) ──
executar_seq_T_033() {
  info 'sequencial T-033 — Controllers REST atualizados'
  if rodar_tarefa seq 'T-033' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-033 — "Controllers REST atualizados"
  critérios/refs: AC-051 (Criação de período com datas válidas), AC-054 (Listagem paginada de períodos), AC-057 (Substituição da lista de feriados com tipo), AC-059 (Substituição da grade de ocupação por slots), AC-062 (Cálculo de dias letivos por mês), AC-065 (Cópia de período com turnos, feriados e grades), AC-067 (Substituição da configuração de turnos)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/controllers/AcademicPeriodController.java, server/src/main/java/com/example/carboncalculator/controllers/LaboratoryScheduleController.java
  mensagem de commit: "T-033 calendario-letivo: Controllers REST atualizados"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-033 calendario-letivo: Controllers REST atualizados (auto-commit do plano)'
    fi
    marcar_concluidas T-033
    verde "✔ T-033 concluída"
    return 0
  fi
  vermelho "✘ T-033 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-033"
  FALHAS="$FALHAS T-033"
  return 1
}

# ── sequencial T-034 (ordem do tasks.md) ──
executar_seq_T_034() {
  info 'sequencial T-034 — Seed data com turnos e slots'
  if rodar_tarefa seq 'T-034' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-034 — "Seed data com turnos e slots"
  critérios/refs: US-018, US-019, US-020, US-024
  arquivos permitidos (e seus testes): server/src/main/resources/db/migration/afterMigrate.sql
  mensagem de commit: "T-034 calendario-letivo: Seed data com turnos e slots"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-034 calendario-letivo: Seed data com turnos e slots (auto-commit do plano)'
    fi
    marcar_concluidas T-034
    verde "✔ T-034 concluída"
    return 0
  fi
  vermelho "✘ T-034 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-034"
  FALHAS="$FALHAS T-034"
  return 1
}

# ── sequencial T-035 (ordem do tasks.md) ──
executar_seq_T_035() {
  info 'sequencial T-035 — Frontend: API client e tipos atualizados'
  if rodar_tarefa seq 'T-035' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-035 — "Frontend: API client e tipos atualizados"
  critérios/refs: US-018, US-019, US-020, US-021, US-022, US-024
  arquivos permitidos (e seus testes): client/src/lib/api/academic-periods.ts, client/src/lib/schemas/academicPeriodSchema.ts
  mensagem de commit: "T-035 calendario-letivo: Frontend: API client e tipos atualizados"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' low >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-035 calendario-letivo: Frontend: API client e tipos atualizados (auto-commit do plano)'
    fi
    marcar_concluidas T-035
    verde "✔ T-035 concluída"
    return 0
  fi
  vermelho "✘ T-035 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-035"
  FALHAS="$FALHAS T-035"
  return 1
}

# ── sequencial T-036 (ordem do tasks.md) ──
executar_seq_T_036() {
  info 'sequencial T-036 — Frontend: página Calendário Letivo (cards + turnos + resumo)'
  if rodar_tarefa seq 'T-036' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-036 — "Frontend: página Calendário Letivo (cards + turnos + resumo)"
  critérios/refs: AC-051 (Criação de período com datas válidas), AC-054 (Listagem paginada de períodos), AC-067 (Substituição da configuração de turnos)
  arquivos permitidos (e seus testes): client/src/pages/academic-periods/AcademicPeriodsPage.tsx, client/src/components/academic-periods/AcademicPeriodCard.tsx, client/src/components/academic-periods/ShiftSummaryTable.tsx, client/src/components/academic-periods/OccupationSummaryGrid.tsx
  mensagem de commit: "T-036 calendario-letivo: Frontend: página Calendário Letivo (cards + turnos + resumo)"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-036 calendario-letivo: Frontend: página Calendário Letivo (cards + turnos + resumo) (auto-commit do plano)'
    fi
    marcar_concluidas T-036
    verde "✔ T-036 concluída"
    return 0
  fi
  vermelho "✘ T-036 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-036"
  FALHAS="$FALHAS T-036"
  return 1
}

# ── sequencial T-037 (ordem do tasks.md) ──
executar_seq_T_037() {
  info 'sequencial T-037 — Frontend: modal Configurar Turnos'
  if rodar_tarefa seq 'T-037' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-037 — "Frontend: modal Configurar Turnos"
  critérios/refs: AC-067 (Substituição da configuração de turnos), AC-068 (Rejeição de turno com classesPerDay zero), AC-069 (Rejeição de turno duplicado no mesmo período)
  arquivos permitidos (e seus testes): client/src/components/academic-periods/ShiftConfigModal.tsx
  mensagem de commit: "T-037 calendario-letivo: Frontend: modal Configurar Turnos"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-037 calendario-letivo: Frontend: modal Configurar Turnos (auto-commit do plano)'
    fi
    marcar_concluidas T-037
    verde "✔ T-037 concluída"
    return 0
  fi
  vermelho "✘ T-037 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-037"
  FALHAS="$FALHAS T-037"
  return 1
}

# ── sequencial T-038 (ordem do tasks.md) ──
executar_seq_T_038() {
  info 'sequencial T-038 — Frontend: página Ocupação dos Laboratórios'
  if rodar_tarefa seq 'T-038' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-038 — "Frontend: página Ocupação dos Laboratórios"
  critérios/refs: AC-059 (Substituição da grade de ocupação por slots), AC-060 (Rejeição de slot fora do range do turno), AC-061 (Rejeição de dia fora dos activeDays do turno)
  arquivos permitidos (e seus testes): client/src/pages/academic-periods/OccupationEditorPage.tsx, client/src/components/academic-periods/OccupationGrid.tsx
  mensagem de commit: "T-038 calendario-letivo: Frontend: página Ocupação dos Laboratórios"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' xhigh >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-038 calendario-letivo: Frontend: página Ocupação dos Laboratórios (auto-commit do plano)'
    fi
    marcar_concluidas T-038
    verde "✔ T-038 concluída"
    return 0
  fi
  vermelho "✘ T-038 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-038"
  FALHAS="$FALHAS T-038"
  return 1
}

# ── sequencial T-039 (ordem do tasks.md) ──
executar_seq_T_039() {
  info 'sequencial T-039 — Frontend: editor de feriados com calendário shadcn'
  if rodar_tarefa seq 'T-039' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-039 — "Frontend: editor de feriados com calendário shadcn"
  critérios/refs: AC-057 (Substituição da lista de feriados com tipo), AC-058 (Rejeição de feriado fora do intervalo)
  arquivos permitidos (e seus testes): client/src/components/academic-periods/HolidayEditor.tsx
  mensagem de commit: "T-039 calendario-letivo: Frontend: editor de feriados com calendário shadcn"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-039 calendario-letivo: Frontend: editor de feriados com calendário shadcn (auto-commit do plano)'
    fi
    marcar_concluidas T-039
    verde "✔ T-039 concluída"
    return 0
  fi
  vermelho "✘ T-039 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-039"
  FALHAS="$FALHAS T-039"
  return 1
}

# ── sequencial T-040 (ordem do tasks.md) ──
executar_seq_T_040() {
  info 'sequencial T-040 — Frontend: sidebar, rotas e formulários'
  if rodar_tarefa seq 'T-040' 'Você executa UMA tarefa da feature "calendario-letivo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/calendario-letivo/spec.md, .spec/features/calendario-letivo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-040 — "Frontend: sidebar, rotas e formulários"
  critérios/refs: AC-055 (Edição de período), AC-065 (Cópia de período com turnos, feriados e grades)
  arquivos permitidos (e seus testes): client/src/components/layout/AppLayout.tsx, client/src/App.tsx, client/src/components/academic-periods/AcademicPeriodForm.tsx, client/src/components/academic-periods/CopyPeriodDialog.tsx
  mensagem de commit: "T-040 calendario-letivo: Frontend: sidebar, rotas e formulários"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-040 calendario-letivo: Frontend: sidebar, rotas e formulários (auto-commit do plano)'
    fi
    marcar_concluidas T-040
    verde "✔ T-040 concluída"
    return 0
  fi
  vermelho "✘ T-040 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/calendario-letivo/executar-tarefas.sh --seq T-040"
  FALHAS="$FALHAS T-040"
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
  executar_seq_T_026 || true
  executar_seq_T_027 || true
  executar_seq_T_028 || true
  executar_seq_T_029 || true
  executar_seq_T_030 || true
  executar_seq_T_031 || true
  executar_seq_T_032 || true
  executar_seq_T_033 || true
  executar_seq_T_034 || true
  executar_seq_T_035 || true
  executar_seq_T_036 || true
  executar_seq_T_037 || true
  executar_seq_T_038 || true
  executar_seq_T_039 || true
  executar_seq_T_040 || true
  encerrar tudo
}

listar() {
  echo "execução: $RUN_ID (feature $FEATURE, branch $BASE_BRANCH)"
  echo "  seq       T-026 (sequencial)"
  echo "  seq       T-027 (sequencial)"
  echo "  seq       T-028 (sequencial)"
  echo "  seq       T-029 (sequencial)"
  echo "  seq       T-030 (sequencial)"
  echo "  seq       T-031 (sequencial)"
  echo "  seq       T-032 (sequencial)"
  echo "  seq       T-033 (sequencial)"
  echo "  seq       T-034 (sequencial)"
  echo "  seq       T-035 (sequencial)"
  echo "  seq       T-036 (sequencial)"
  echo "  seq       T-037 (sequencial)"
  echo "  seq       T-038 (sequencial)"
  echo "  seq       T-039 (sequencial)"
  echo "  seq       T-040 (sequencial)"
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
      T-026) evento --tipo inicio --escopo "seq:T-026"; iniciar_resumos; executar_seq_T_026 || true; encerrar "seq:T-026" ;;
      T-027) evento --tipo inicio --escopo "seq:T-027"; iniciar_resumos; executar_seq_T_027 || true; encerrar "seq:T-027" ;;
      T-028) evento --tipo inicio --escopo "seq:T-028"; iniciar_resumos; executar_seq_T_028 || true; encerrar "seq:T-028" ;;
      T-029) evento --tipo inicio --escopo "seq:T-029"; iniciar_resumos; executar_seq_T_029 || true; encerrar "seq:T-029" ;;
      T-030) evento --tipo inicio --escopo "seq:T-030"; iniciar_resumos; executar_seq_T_030 || true; encerrar "seq:T-030" ;;
      T-031) evento --tipo inicio --escopo "seq:T-031"; iniciar_resumos; executar_seq_T_031 || true; encerrar "seq:T-031" ;;
      T-032) evento --tipo inicio --escopo "seq:T-032"; iniciar_resumos; executar_seq_T_032 || true; encerrar "seq:T-032" ;;
      T-033) evento --tipo inicio --escopo "seq:T-033"; iniciar_resumos; executar_seq_T_033 || true; encerrar "seq:T-033" ;;
      T-034) evento --tipo inicio --escopo "seq:T-034"; iniciar_resumos; executar_seq_T_034 || true; encerrar "seq:T-034" ;;
      T-035) evento --tipo inicio --escopo "seq:T-035"; iniciar_resumos; executar_seq_T_035 || true; encerrar "seq:T-035" ;;
      T-036) evento --tipo inicio --escopo "seq:T-036"; iniciar_resumos; executar_seq_T_036 || true; encerrar "seq:T-036" ;;
      T-037) evento --tipo inicio --escopo "seq:T-037"; iniciar_resumos; executar_seq_T_037 || true; encerrar "seq:T-037" ;;
      T-038) evento --tipo inicio --escopo "seq:T-038"; iniciar_resumos; executar_seq_T_038 || true; encerrar "seq:T-038" ;;
      T-039) evento --tipo inicio --escopo "seq:T-039"; iniciar_resumos; executar_seq_T_039 || true; encerrar "seq:T-039" ;;
      T-040) evento --tipo inicio --escopo "seq:T-040"; iniciar_resumos; executar_seq_T_040 || true; encerrar "seq:T-040" ;;
      *) falhar "tarefa sequencial desconhecida: '$ALVO' — veja as disponíveis com --listar" ;;
    esac ;;
esac
