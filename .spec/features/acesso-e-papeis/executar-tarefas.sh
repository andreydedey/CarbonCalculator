#!/usr/bin/env bash
# executar-tarefas.sh — gerado por `onp-spec plano acesso-e-papeis` em 2026-09-21 02:58
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
# resumo do que está rolando, a qualquer momento: onp-spec resumo acesso-e-papeis
set -u
set -o pipefail

RUN_ID='CarbonCalculatorTCC-acesso-e-papeis-muanoueq'
FEATURE='acesso-e-papeis'
BASE_BRANCH='spec/acesso-e-papeis'
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
  git ls-files --error-unmatch -- '.spec/features/acesso-e-papeis/spec.md' >/dev/null 2>&1 || falhar "spec.md não está commitada — os worktrees das faixas precisam dela no git"
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
  LOG_DIR="$(dirname "$TOPLEVEL")/onp-worktrees/CarbonCalculatorTCC-acesso-e-papeis-logs"
  WT_BASE="$(dirname "$TOPLEVEL")/onp-worktrees/CarbonCalculatorTCC-acesso-e-papeis"
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
    amarelo "  reexecute só ela: bash .spec/features/acesso-e-papeis/executar-tarefas.sh --faixa $1"
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

# ── faixa-1: T-010 ──
executar_faixa_1() {
  local WT="$WT_BASE-faixa-1"
  preparar_worktree 'faixa-1' 'spec/acesso-e-papeis-faixa-1' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-1' --estado executando --tentativa "$(tentativa 'faixa-1')"
  : > "$LOG_DIR/faixa-1.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-1' 'T-010' 'Você executa UMA tarefa da feature "acesso-e-papeis" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acesso-e-papeis/spec.md, .spec/features/acesso-e-papeis/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-010 — "Google OAuth2"
  critérios/refs: AC-020 (Google OAuth cria ou vincula conta e retorna JWT)
  arquivos permitidos (e seus testes): server/pom.xml, server/src/main/java/com/example/carboncalculator/security/OAuth2SuccessHandler.java, server/src/main/resources/application.yaml
  mensagem de commit: "T-010 acesso-e-papeis: Google OAuth2"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high
  ) >> "$LOG_DIR/faixa-1.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-1' 'spec/acesso-e-papeis-faixa-1' "$WT" "$st" || return 1
  marcar_concluidas T-010
  return 0
}

# ── faixa-2: T-011 ──
executar_faixa_2() {
  local WT="$WT_BASE-faixa-2"
  preparar_worktree 'faixa-2' 'spec/acesso-e-papeis-faixa-2' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-2' --estado executando --tentativa "$(tentativa 'faixa-2')"
  : > "$LOG_DIR/faixa-2.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-2' 'T-011' 'Você executa UMA tarefa da feature "acesso-e-papeis" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acesso-e-papeis/spec.md, .spec/features/acesso-e-papeis/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-011 — "TenantFilter: validar vínculo user↔institution"
  critérios/refs: AC-021 (Admin acessa e gerencia todas as instituições), AC-024 (Usuário sem vínculo não acessa dados da instituição)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/config/TenantFilter.java
  mensagem de commit: "T-011 acesso-e-papeis: TenantFilter: validar vínculo user↔institution"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium
  ) >> "$LOG_DIR/faixa-2.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-2' 'spec/acesso-e-papeis-faixa-2' "$WT" "$st" || return 1
  marcar_concluidas T-011
  return 0
}

# ── faixa-3: T-012 ──
executar_faixa_3() {
  local WT="$WT_BASE-faixa-3"
  preparar_worktree 'faixa-3' 'spec/acesso-e-papeis-faixa-3' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-3' --estado executando --tentativa "$(tentativa 'faixa-3')"
  : > "$LOG_DIR/faixa-3.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-3' 'T-012' 'Você executa UMA tarefa da feature "acesso-e-papeis" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acesso-e-papeis/spec.md, .spec/features/acesso-e-papeis/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-012 — "@PreAuthorize em controllers existentes"
  critérios/refs: AC-021 (Admin acessa e gerencia todas as instituições), AC-022 (Gestor pode criar, editar e excluir dados da sua instituição), AC-023 (Pesquisador tem acesso somente leitura), AC-025 (Hierarquia de papéis funciona corretamente)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/controllers/InstitutionController.java, server/src/main/java/com/example/carboncalculator/controllers/LaboratoryController.java
  mensagem de commit: "T-012 acesso-e-papeis: @PreAuthorize em controllers existentes"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' low
  ) >> "$LOG_DIR/faixa-3.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-3' 'spec/acesso-e-papeis-faixa-3' "$WT" "$st" || return 1
  marcar_concluidas T-012
  return 0
}

# ── faixa-4: T-015 ──
executar_faixa_4() {
  local WT="$WT_BASE-faixa-4"
  preparar_worktree 'faixa-4' 'spec/acesso-e-papeis-faixa-4' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-4' --estado executando --tentativa "$(tentativa 'faixa-4')"
  : > "$LOG_DIR/faixa-4.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-4' 'T-015' 'Você executa UMA tarefa da feature "acesso-e-papeis" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acesso-e-papeis/spec.md, .spec/features/acesso-e-papeis/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-015 — "Frontend: página de login"
  critérios/refs: AC-014 (Login com credenciais válidas retorna JWT), AC-015 (Login com credenciais inválidas é recusado), AC-020 (Google OAuth cria ou vincula conta e retorna JWT)
  arquivos permitidos (e seus testes): client/src/pages/auth/LoginPage.tsx, client/src/lib/schemas/loginSchema.ts
  mensagem de commit: "T-015 acesso-e-papeis: Frontend: página de login"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium
  ) >> "$LOG_DIR/faixa-4.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-4' 'spec/acesso-e-papeis-faixa-4' "$WT" "$st" || return 1
  marcar_concluidas T-015
  return 0
}

# ── faixa-5: T-016 ──
executar_faixa_5() {
  local WT="$WT_BASE-faixa-5"
  preparar_worktree 'faixa-5' 'spec/acesso-e-papeis-faixa-5' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-5' --estado executando --tentativa "$(tentativa 'faixa-5')"
  : > "$LOG_DIR/faixa-5.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-5' 'T-016' 'Você executa UMA tarefa da feature "acesso-e-papeis" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acesso-e-papeis/spec.md, .spec/features/acesso-e-papeis/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-016 — "Frontend: página de registro"
  critérios/refs: AC-017 (Registro com dados válidos cria conta e retorna JWT), AC-018 (Registro com email já existente é recusado), AC-020 (Google OAuth cria ou vincula conta e retorna JWT)
  arquivos permitidos (e seus testes): client/src/pages/auth/RegisterPage.tsx, client/src/lib/schemas/registerSchema.ts
  mensagem de commit: "T-016 acesso-e-papeis: Frontend: página de registro"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium
  ) >> "$LOG_DIR/faixa-5.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-5' 'spec/acesso-e-papeis-faixa-5' "$WT" "$st" || return 1
  marcar_concluidas T-016
  return 0
}

# ── faixa-6: T-017 ──
executar_faixa_6() {
  local WT="$WT_BASE-faixa-6"
  preparar_worktree 'faixa-6' 'spec/acesso-e-papeis-faixa-6' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-6' --estado executando --tentativa "$(tentativa 'faixa-6')"
  : > "$LOG_DIR/faixa-6.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-6' 'T-017' 'Você executa UMA tarefa da feature "acesso-e-papeis" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acesso-e-papeis/spec.md, .spec/features/acesso-e-papeis/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-017 — "Frontend: página de gestão de usuários"
  critérios/refs: AC-026 (Gestor convida usuário por email), AC-027 (Convite duplicado é recusado), AC-028 (Gestor altera papel de membro), AC-029 (Gestor revoga acesso de membro), AC-030 (Gestor não pode revogar o próprio acesso), AC-031 (Gestor não pode alterar o próprio papel)
  arquivos permitidos (e seus testes): client/src/pages/users/UsersPage.tsx, client/src/components/users/UsersTable.tsx, client/src/components/users/InviteDialog.tsx, client/src/lib/api/users.ts, client/src/lib/schemas/inviteSchema.ts
  mensagem de commit: "T-017 acesso-e-papeis: Frontend: página de gestão de usuários"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high
  ) >> "$LOG_DIR/faixa-6.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-6' 'spec/acesso-e-papeis-faixa-6' "$WT" "$st" || return 1
  marcar_concluidas T-017
  return 0
}

# ── faixa-7: T-018 ──
executar_faixa_7() {
  local WT="$WT_BASE-faixa-7"
  preparar_worktree 'faixa-7' 'spec/acesso-e-papeis-faixa-7' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-7' --estado executando --tentativa "$(tentativa 'faixa-7')"
  : > "$LOG_DIR/faixa-7.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-7' 'T-018' 'Você executa UMA tarefa da feature "acesso-e-papeis" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acesso-e-papeis/spec.md, .spec/features/acesso-e-papeis/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-018 — "Frontend: página admin de instituições"
  critérios/refs: AC-021 (Admin acessa e gerencia todas as instituições)
  arquivos permitidos (e seus testes): client/src/pages/admin/AdminInstitutionsPage.tsx, client/src/components/admin/InstitutionCard.tsx
  mensagem de commit: "T-018 acesso-e-papeis: Frontend: página admin de instituições"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium
  ) >> "$LOG_DIR/faixa-7.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-7' 'spec/acesso-e-papeis-faixa-7' "$WT" "$st" || return 1
  marcar_concluidas T-018
  return 0
}

# ── sequencial T-006 (fora da seleção do usuário) ──
executar_seq_T_006() {
  info 'sequencial T-006 — Migrations e entidades JPA (app_user, user_institution, public_results)'
  if rodar_tarefa seq 'T-006' 'Você executa UMA tarefa da feature "acesso-e-papeis" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acesso-e-papeis/spec.md, .spec/features/acesso-e-papeis/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-006 — "Migrations e entidades JPA (app_user, user_institution, public_results)"
  critérios/refs: US-006, US-007, US-009, US-010
  arquivos permitidos (e seus testes): server/src/main/resources/db/migration/V4__create_app_user_table.sql, server/src/main/resources/db/migration/V5__create_user_institution_table.sql, server/src/main/resources/db/migration/V6__add_public_results_to_institution.sql, server/src/main/java/com/example/carboncalculator/models/AppUser.java, server/src/main/java/com/example/carboncalculator/models/UserInstitution.java, server/src/main/java/com/example/carboncalculator/models/InstitutionRole.java, server/src/main/java/com/example/carboncalculator/models/MembershipStatus.java, server/src/main/java/com/example/carboncalculator/repositories/AppUserRepository.java, server/src/main/java/com/example/carboncalculator/repositories/UserInstitutionRepository.java
  mensagem de commit: "T-006 acesso-e-papeis: Migrations e entidades JPA (app_user, user_institution, public_results)"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-006 acesso-e-papeis: Migrations e entidades JPA (app_user, user_institution, public_results) (auto-commit do plano)'
    fi
    marcar_concluidas T-006
    verde "✔ T-006 concluída"
    return 0
  fi
  vermelho "✘ T-006 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/acesso-e-papeis/executar-tarefas.sh --seq T-006"
  FALHAS="$FALHAS T-006"
  return 1
}

# ── sequencial T-007 (fora da seleção do usuário) ──
executar_seq_T_007() {
  info 'sequencial T-007 — JWT service (geração, validação, refresh)'
  if rodar_tarefa seq 'T-007' 'Você executa UMA tarefa da feature "acesso-e-papeis" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acesso-e-papeis/spec.md, .spec/features/acesso-e-papeis/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-007 — "JWT service (geração, validação, refresh)"
  critérios/refs: AC-014 (Login com credenciais válidas retorna JWT), AC-015 (Login com credenciais inválidas é recusado), AC-032 (Refresh token renova o access token), AC-033 (Refresh com token expirado é recusado)
  arquivos permitidos (e seus testes): server/pom.xml, server/src/main/java/com/example/carboncalculator/security/JwtService.java, server/src/main/java/com/example/carboncalculator/security/JwtAuthFilter.java, server/src/main/resources/application.yaml
  mensagem de commit: "T-007 acesso-e-papeis: JWT service (geração, validação, refresh)"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-007 acesso-e-papeis: JWT service (geração, validação, refresh) (auto-commit do plano)'
    fi
    marcar_concluidas T-007
    verde "✔ T-007 concluída"
    return 0
  fi
  vermelho "✘ T-007 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/acesso-e-papeis/executar-tarefas.sh --seq T-007"
  FALHAS="$FALHAS T-007"
  return 1
}

# ── sequencial T-008 (fora da seleção do usuário) ──
executar_seq_T_008() {
  info 'sequencial T-008 — SecurityFilterChain e role hierarchy'
  if rodar_tarefa seq 'T-008' 'Você executa UMA tarefa da feature "acesso-e-papeis" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acesso-e-papeis/spec.md, .spec/features/acesso-e-papeis/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-008 — "SecurityFilterChain e role hierarchy"
  critérios/refs: AC-016 (Acesso sem autenticação é bloqueado), AC-025 (Hierarquia de papéis funciona corretamente)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/config/SecurityConfig.java
  mensagem de commit: "T-008 acesso-e-papeis: SecurityFilterChain e role hierarchy"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-008 acesso-e-papeis: SecurityFilterChain e role hierarchy (auto-commit do plano)'
    fi
    marcar_concluidas T-008
    verde "✔ T-008 concluída"
    return 0
  fi
  vermelho "✘ T-008 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/acesso-e-papeis/executar-tarefas.sh --seq T-008"
  FALHAS="$FALHAS T-008"
  return 1
}

# ── sequencial T-009 (fora da seleção do usuário) ──
executar_seq_T_009() {
  info 'sequencial T-009 — AuthController (register, login, refresh, me)'
  if rodar_tarefa seq 'T-009' 'Você executa UMA tarefa da feature "acesso-e-papeis" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acesso-e-papeis/spec.md, .spec/features/acesso-e-papeis/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-009 — "AuthController (register, login, refresh, me)"
  critérios/refs: AC-014 (Login com credenciais válidas retorna JWT), AC-015 (Login com credenciais inválidas é recusado), AC-016 (Acesso sem autenticação é bloqueado), AC-017 (Registro com dados válidos cria conta e retorna JWT), AC-018 (Registro com email já existente é recusado), AC-019 (Convites pendentes são ativados no registro), AC-032 (Refresh token renova o access token), AC-033 (Refresh com token expirado é recusado)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/controllers/AuthController.java, server/src/main/java/com/example/carboncalculator/services/AuthService.java, server/src/main/java/com/example/carboncalculator/dto/LoginRequest.java, server/src/main/java/com/example/carboncalculator/dto/RegisterRequest.java, server/src/main/java/com/example/carboncalculator/dto/AuthResponse.java, server/src/main/java/com/example/carboncalculator/dto/UserProfileDTO.java
  mensagem de commit: "T-009 acesso-e-papeis: AuthController (register, login, refresh, me)"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-009 acesso-e-papeis: AuthController (register, login, refresh, me) (auto-commit do plano)'
    fi
    marcar_concluidas T-009
    verde "✔ T-009 concluída"
    return 0
  fi
  vermelho "✘ T-009 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/acesso-e-papeis/executar-tarefas.sh --seq T-009"
  FALHAS="$FALHAS T-009"
  return 1
}

# ── sequencial T-013 (fora da seleção do usuário) ──
executar_seq_T_013() {
  info 'sequencial T-013 — UserController e UserService (gestão de membros)'
  if rodar_tarefa seq 'T-013' 'Você executa UMA tarefa da feature "acesso-e-papeis" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acesso-e-papeis/spec.md, .spec/features/acesso-e-papeis/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-013 — "UserController e UserService (gestão de membros)"
  critérios/refs: AC-026 (Gestor convida usuário por email), AC-027 (Convite duplicado é recusado), AC-028 (Gestor altera papel de membro), AC-029 (Gestor revoga acesso de membro), AC-030 (Gestor não pode revogar o próprio acesso), AC-031 (Gestor não pode alterar o próprio papel)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/controllers/UserController.java, server/src/main/java/com/example/carboncalculator/services/UserService.java, server/src/main/java/com/example/carboncalculator/dto/InviteRequest.java, server/src/main/java/com/example/carboncalculator/dto/ChangeRoleRequest.java, server/src/main/java/com/example/carboncalculator/dto/MemberDTO.java
  mensagem de commit: "T-013 acesso-e-papeis: UserController e UserService (gestão de membros)"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-013 acesso-e-papeis: UserController e UserService (gestão de membros) (auto-commit do plano)'
    fi
    marcar_concluidas T-013
    verde "✔ T-013 concluída"
    return 0
  fi
  vermelho "✘ T-013 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/acesso-e-papeis/executar-tarefas.sh --seq T-013"
  FALHAS="$FALHAS T-013"
  return 1
}

# ── sequencial T-014 (fora da seleção do usuário) ──
executar_seq_T_014() {
  info 'sequencial T-014 — Frontend: AuthContext, interceptor e rotas protegidas'
  if rodar_tarefa seq 'T-014' 'Você executa UMA tarefa da feature "acesso-e-papeis" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acesso-e-papeis/spec.md, .spec/features/acesso-e-papeis/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-014 — "Frontend: AuthContext, interceptor e rotas protegidas"
  critérios/refs: AC-014 (Login com credenciais válidas retorna JWT), AC-016 (Acesso sem autenticação é bloqueado), AC-032 (Refresh token renova o access token)
  arquivos permitidos (e seus testes): client/src/context/AuthContext.tsx, client/src/components/auth/ProtectedRoute.tsx, client/src/lib/api/client.ts, client/src/App.tsx
  mensagem de commit: "T-014 acesso-e-papeis: Frontend: AuthContext, interceptor e rotas protegidas"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-014 acesso-e-papeis: Frontend: AuthContext, interceptor e rotas protegidas (auto-commit do plano)'
    fi
    marcar_concluidas T-014
    verde "✔ T-014 concluída"
    return 0
  fi
  vermelho "✘ T-014 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/acesso-e-papeis/executar-tarefas.sh --seq T-014"
  FALHAS="$FALHAS T-014"
  return 1
}

# ── sequencial T-019 (fora da seleção do usuário) ──
executar_seq_T_019() {
  info 'sequencial T-019 — Frontend: atualizações no layout (sidebar, topbar, institution switcher)'
  if rodar_tarefa seq 'T-019' 'Você executa UMA tarefa da feature "acesso-e-papeis" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acesso-e-papeis/spec.md, .spec/features/acesso-e-papeis/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-019 — "Frontend: atualizações no layout (sidebar, topbar, institution switcher)"
  critérios/refs: AC-021 (Admin acessa e gerencia todas as instituições)
  arquivos permitidos (e seus testes): client/src/components/layout/AppLayout.tsx, client/src/components/layout/AppSidebar.tsx, client/src/components/layout/InstitutionSwitcher.tsx
  mensagem de commit: "T-019 acesso-e-papeis: Frontend: atualizações no layout (sidebar, topbar, institution switcher)"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-019 acesso-e-papeis: Frontend: atualizações no layout (sidebar, topbar, institution switcher) (auto-commit do plano)'
    fi
    marcar_concluidas T-019
    verde "✔ T-019 concluída"
    return 0
  fi
  vermelho "✘ T-019 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/acesso-e-papeis/executar-tarefas.sh --seq T-019"
  FALHAS="$FALHAS T-019"
  return 1
}

# ── sequencial T-020 (fora da seleção do usuário) ──
executar_seq_T_020() {
  info 'sequencial T-020 — Testes de integração (auth + autorização + cross-tenant)'
  if rodar_tarefa seq 'T-020' 'Você executa UMA tarefa da feature "acesso-e-papeis" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/acesso-e-papeis/spec.md, .spec/features/acesso-e-papeis/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-020 — "Testes de integração (auth + autorização + cross-tenant)"
  critérios/refs: AC-014 (Login com credenciais válidas retorna JWT), AC-015 (Login com credenciais inválidas é recusado), AC-016 (Acesso sem autenticação é bloqueado), AC-017 (Registro com dados válidos cria conta e retorna JWT), AC-018 (Registro com email já existente é recusado), AC-019 (Convites pendentes são ativados no registro), AC-021 (Admin acessa e gerencia todas as instituições), AC-022 (Gestor pode criar, editar e excluir dados da sua instituição), AC-023 (Pesquisador tem acesso somente leitura), AC-024 (Usuário sem vínculo não acessa dados da instituição), AC-025 (Hierarquia de papéis funciona corretamente), AC-026 (Gestor convida usuário por email), AC-027 (Convite duplicado é recusado), AC-028 (Gestor altera papel de membro), AC-029 (Gestor revoga acesso de membro), AC-030 (Gestor não pode revogar o próprio acesso), AC-031 (Gestor não pode alterar o próprio papel), AC-032 (Refresh token renova o access token), AC-033 (Refresh com token expirado é recusado)
  arquivos permitidos (e seus testes): server/src/test/java/com/example/carboncalculator/controllers/AuthControllerTest.java, server/src/test/java/com/example/carboncalculator/controllers/UserControllerTest.java, server/src/test/java/com/example/carboncalculator/controllers/LaboratoryControllerAuthTest.java
  mensagem de commit: "T-020 acesso-e-papeis: Testes de integração (auth + autorização + cross-tenant)"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test --test-reporter=tap "client/src/**/*.test.ts"` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-020 acesso-e-papeis: Testes de integração (auth + autorização + cross-tenant) (auto-commit do plano)'
    fi
    marcar_concluidas T-020
    verde "✔ T-020 concluída"
    return 0
  fi
  vermelho "✘ T-020 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/acesso-e-papeis/executar-tarefas.sh --seq T-020"
  FALHAS="$FALHAS T-020"
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
      amarelo "  para o veredito: bash .spec/features/acesso-e-papeis/executar-tarefas.sh --gate"
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
  # onda 3: faixa-7
  info "onda 3: faixa-7 — janelas limpas em paralelo"
  executar_faixa_7 & PID_FAIXA_7=$!
  wait "$PID_FAIXA_7" || true
  executar_seq_T_006 || true
  executar_seq_T_007 || true
  executar_seq_T_008 || true
  executar_seq_T_009 || true
  executar_seq_T_013 || true
  executar_seq_T_014 || true
  executar_seq_T_019 || true
  executar_seq_T_020 || true
  encerrar tudo
}

listar() {
  echo "execução: $RUN_ID (feature $FEATURE, branch $BASE_BRANCH)"
  echo "  faixa-1  onda 1  T-010"
  echo "  faixa-2  onda 1  T-011"
  echo "  faixa-3  onda 1  T-012"
  echo "  faixa-4  onda 2  T-015"
  echo "  faixa-5  onda 2  T-016"
  echo "  faixa-6  onda 2  T-017"
  echo "  faixa-7  onda 3  T-018"
  echo "  seq       T-006 (sequencial)"
  echo "  seq       T-007 (sequencial)"
  echo "  seq       T-008 (sequencial)"
  echo "  seq       T-009 (sequencial)"
  echo "  seq       T-013 (sequencial)"
  echo "  seq       T-014 (sequencial)"
  echo "  seq       T-019 (sequencial)"
  echo "  seq       T-020 (sequencial)"
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
      *) falhar "faixa desconhecida: '$ALVO' — veja as disponíveis com --listar" ;;
    esac ;;
  seq)
    case "$ALVO" in
      T-006) evento --tipo inicio --escopo "seq:T-006"; iniciar_resumos; executar_seq_T_006 || true; encerrar "seq:T-006" ;;
      T-007) evento --tipo inicio --escopo "seq:T-007"; iniciar_resumos; executar_seq_T_007 || true; encerrar "seq:T-007" ;;
      T-008) evento --tipo inicio --escopo "seq:T-008"; iniciar_resumos; executar_seq_T_008 || true; encerrar "seq:T-008" ;;
      T-009) evento --tipo inicio --escopo "seq:T-009"; iniciar_resumos; executar_seq_T_009 || true; encerrar "seq:T-009" ;;
      T-013) evento --tipo inicio --escopo "seq:T-013"; iniciar_resumos; executar_seq_T_013 || true; encerrar "seq:T-013" ;;
      T-014) evento --tipo inicio --escopo "seq:T-014"; iniciar_resumos; executar_seq_T_014 || true; encerrar "seq:T-014" ;;
      T-019) evento --tipo inicio --escopo "seq:T-019"; iniciar_resumos; executar_seq_T_019 || true; encerrar "seq:T-019" ;;
      T-020) evento --tipo inicio --escopo "seq:T-020"; iniciar_resumos; executar_seq_T_020 || true; encerrar "seq:T-020" ;;
      *) falhar "tarefa sequencial desconhecida: '$ALVO' — veja as disponíveis com --listar" ;;
    esac ;;
esac
