#!/usr/bin/env bash
# executar-tarefas.sh — gerado por `onp-spec plano registro-por-convite` em 2026-10-08 15:02
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
# resumo do que está rolando, a qualquer momento: onp-spec resumo registro-por-convite
set -u
set -o pipefail

RUN_ID='CarbonCalculatorTCC-registro-por-convite-muzo0diz'
FEATURE='registro-por-convite'
BASE_BRANCH='spec/registro-por-convite'
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
  git ls-files --error-unmatch -- '.spec/features/registro-por-convite/spec.md' >/dev/null 2>&1 || falhar "spec.md não está commitada — os worktrees das faixas precisam dela no git"
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
  LOG_DIR="$(dirname "$TOPLEVEL")/onp-worktrees/CarbonCalculatorTCC-registro-por-convite-logs"
  WT_BASE="$(dirname "$TOPLEVEL")/onp-worktrees/CarbonCalculatorTCC-registro-por-convite"
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
    amarelo "  reexecute só ela: bash .spec/features/registro-por-convite/executar-tarefas.sh --faixa $1"
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

# ── sequencial T-060 (ordem do tasks.md) ──
executar_seq_T_060() {
  info 'sequencial T-060 — Migration: colunas de token no user_institution'
  if rodar_tarefa seq 'T-060' 'Você executa UMA tarefa da feature "registro-por-convite" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/registro-por-convite/spec.md, .spec/features/registro-por-convite/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-060 — "Migration: colunas de token no user_institution"
  critérios/refs: AC-155 (Resposta do convite inclui link), AC-156 (Token armazenado como hash), AC-157 (Token expira em 7 dias)
  arquivos permitidos (e seus testes): server/src/main/resources/db/migration/V24__add_invite_token_to_user_institution.sql
  mensagem de commit: "T-060 registro-por-convite: Migration: colunas de token no user_institution"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-060 registro-por-convite: Migration: colunas de token no user_institution (auto-commit do plano)'
    fi
    marcar_concluidas T-060
    verde "✔ T-060 concluída"
    return 0
  fi
  vermelho "✘ T-060 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/registro-por-convite/executar-tarefas.sh --seq T-060"
  FALHAS="$FALHAS T-060"
  return 1
}

# ── sequencial T-061 (ordem do tasks.md) ──
executar_seq_T_061() {
  info 'sequencial T-061 — Backend: InviteTokenService + alterar UserInstitution entity'
  if rodar_tarefa seq 'T-061' 'Você executa UMA tarefa da feature "registro-por-convite" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/registro-por-convite/spec.md, .spec/features/registro-por-convite/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-061 — "Backend: InviteTokenService + alterar UserInstitution entity"
  critérios/refs: AC-155 (Resposta do convite inclui link), AC-156 (Token armazenado como hash), AC-157 (Token expira em 7 dias), AC-158 (Convite de usuário já registrado não gera link)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/services/InviteTokenService.java, server/src/main/java/com/example/carboncalculator/entities/UserInstitution.java
  mensagem de commit: "T-061 registro-por-convite: Backend: InviteTokenService + alterar UserInstitution entity"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-061 registro-por-convite: Backend: InviteTokenService + alterar UserInstitution entity (auto-commit do plano)'
    fi
    marcar_concluidas T-061
    verde "✔ T-061 concluída"
    return 0
  fi
  vermelho "✘ T-061 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/registro-por-convite/executar-tarefas.sh --seq T-061"
  FALHAS="$FALHAS T-061"
  return 1
}

# ── sequencial T-062 (ordem do tasks.md) ──
executar_seq_T_062() {
  info 'sequencial T-062 — Backend: alterar UserService.invite() para gerar token'
  if rodar_tarefa seq 'T-062' 'Você executa UMA tarefa da feature "registro-por-convite" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/registro-por-convite/spec.md, .spec/features/registro-por-convite/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-062 — "Backend: alterar UserService.invite() para gerar token"
  critérios/refs: AC-155 (Resposta do convite inclui link), AC-156 (Token armazenado como hash), AC-157 (Token expira em 7 dias), AC-158 (Convite de usuário já registrado não gera link)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/services/UserService.java, server/src/main/java/com/example/carboncalculator/controllers/UserController.java, server/src/main/java/com/example/carboncalculator/dto/UserMemberDTO.java
  mensagem de commit: "T-062 registro-por-convite: Backend: alterar UserService.invite() para gerar token"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-062 registro-por-convite: Backend: alterar UserService.invite() para gerar token (auto-commit do plano)'
    fi
    marcar_concluidas T-062
    verde "✔ T-062 concluída"
    return 0
  fi
  vermelho "✘ T-062 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/registro-por-convite/executar-tarefas.sh --seq T-062"
  FALHAS="$FALHAS T-062"
  return 1
}

# ── sequencial T-063 (ordem do tasks.md) ──
executar_seq_T_063() {
  info 'sequencial T-063 — Backend: endpoints de validação e aceitação de convite'
  if rodar_tarefa seq 'T-063' 'Você executa UMA tarefa da feature "registro-por-convite" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/registro-por-convite/spec.md, .spec/features/registro-por-convite/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-063 — "Backend: endpoints de validação e aceitação de convite"
  critérios/refs: AC-159 (Validação de token retorna dados do convite), AC-160 (Token inválido é rejeitado na validação), AC-161 (Aceitar convite cria conta e ativa vínculo), AC-162 (Token consumido não pode ser reutilizado), AC-163 (Aceitar convite ativa outros convites PENDING do mesmo email), AC-164 (Rejeitar aceitação se email já registrado)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/controllers/AuthController.java, server/src/main/java/com/example/carboncalculator/services/AuthService.java, server/src/main/java/com/example/carboncalculator/config/SecurityConfig.java
  mensagem de commit: "T-063 registro-por-convite: Backend: endpoints de validação e aceitação de convite"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-063 registro-por-convite: Backend: endpoints de validação e aceitação de convite (auto-commit do plano)'
    fi
    marcar_concluidas T-063
    verde "✔ T-063 concluída"
    return 0
  fi
  vermelho "✘ T-063 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/registro-por-convite/executar-tarefas.sh --seq T-063"
  FALHAS="$FALHAS T-063"
  return 1
}

# ── sequencial T-064 (ordem do tasks.md) ──
executar_seq_T_064() {
  info 'sequencial T-064 — Backend: remover endpoint POST /auth/register'
  if rodar_tarefa seq 'T-064' 'Você executa UMA tarefa da feature "registro-por-convite" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/registro-por-convite/spec.md, .spec/features/registro-por-convite/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-064 — "Backend: remover endpoint POST /auth/register"
  critérios/refs: AC-167 (Endpoint POST /auth/register removido)
  arquivos permitidos (e seus testes): server/src/main/java/com/example/carboncalculator/controllers/AuthController.java, server/src/main/java/com/example/carboncalculator/services/AuthService.java, server/src/main/java/com/example/carboncalculator/config/SecurityConfig.java
  mensagem de commit: "T-064 registro-por-convite: Backend: remover endpoint POST /auth/register"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-064 registro-por-convite: Backend: remover endpoint POST /auth/register (auto-commit do plano)'
    fi
    marcar_concluidas T-064
    verde "✔ T-064 concluída"
    return 0
  fi
  vermelho "✘ T-064 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/registro-por-convite/executar-tarefas.sh --seq T-064"
  FALHAS="$FALHAS T-064"
  return 1
}

# ── sequencial T-065 (ordem do tasks.md) ──
executar_seq_T_065() {
  info 'sequencial T-065 — Frontend: refatorar RegisterPage para exigir token'
  if rodar_tarefa seq 'T-065' 'Você executa UMA tarefa da feature "registro-por-convite" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/registro-por-convite/spec.md, .spec/features/registro-por-convite/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-065 — "Frontend: refatorar RegisterPage para exigir token"
  critérios/refs: AC-165 (Formulário de registro exibe email readonly), AC-166 (Página de registro sem token mostra aviso)
  arquivos permitidos (e seus testes): client/src/pages/auth/RegisterPage.tsx, client/src/lib/api/auth.ts, client/src/context/AuthContext.tsx, client/src/lib/schemas/authSchemas.ts
  mensagem de commit: "T-065 registro-por-convite: Frontend: refatorar RegisterPage para exigir token"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-065 registro-por-convite: Frontend: refatorar RegisterPage para exigir token (auto-commit do plano)'
    fi
    marcar_concluidas T-065
    verde "✔ T-065 concluída"
    return 0
  fi
  vermelho "✘ T-065 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/registro-por-convite/executar-tarefas.sh --seq T-065"
  FALHAS="$FALHAS T-065"
  return 1
}

# ── sequencial T-066 (ordem do tasks.md) ──
executar_seq_T_066() {
  info 'sequencial T-066 — Frontend: refatorar LoginPage (remover link de registro)'
  if rodar_tarefa seq 'T-066' 'Você executa UMA tarefa da feature "registro-por-convite" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/registro-por-convite/spec.md, .spec/features/registro-por-convite/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-066 — "Frontend: refatorar LoginPage (remover link de registro)"
  critérios/refs: AC-168 (Página de login sem link de registro)
  arquivos permitidos (e seus testes): client/src/pages/auth/LoginPage.tsx
  mensagem de commit: "T-066 registro-por-convite: Frontend: refatorar LoginPage (remover link de registro)"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-066 registro-por-convite: Frontend: refatorar LoginPage (remover link de registro) (auto-commit do plano)'
    fi
    marcar_concluidas T-066
    verde "✔ T-066 concluída"
    return 0
  fi
  vermelho "✘ T-066 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/registro-por-convite/executar-tarefas.sh --seq T-066"
  FALHAS="$FALHAS T-066"
  return 1
}

# ── sequencial T-067 (ordem do tasks.md) ──
executar_seq_T_067() {
  info 'sequencial T-067 — Frontend: dialog de link de convite na UsersPage'
  if rodar_tarefa seq 'T-067' 'Você executa UMA tarefa da feature "registro-por-convite" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/registro-por-convite/spec.md, .spec/features/registro-por-convite/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-067 — "Frontend: dialog de link de convite na UsersPage"
  critérios/refs: AC-169 (Dialog exibe link copiável após convite), AC-170 (Botão copiar funciona)
  arquivos permitidos (e seus testes): client/src/pages/users/UsersPage.tsx, client/src/lib/api/users.ts
  mensagem de commit: "T-067 registro-por-convite: Frontend: dialog de link de convite na UsersPage"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-067 registro-por-convite: Frontend: dialog de link de convite na UsersPage (auto-commit do plano)'
    fi
    marcar_concluidas T-067
    verde "✔ T-067 concluída"
    return 0
  fi
  vermelho "✘ T-067 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/registro-por-convite/executar-tarefas.sh --seq T-067"
  FALHAS="$FALHAS T-067"
  return 1
}

# ── sequencial T-068 (ordem do tasks.md) ──
executar_seq_T_068() {
  info 'sequencial T-068 — Testes de integração backend'
  if rodar_tarefa seq 'T-068' 'Você executa UMA tarefa da feature "registro-por-convite" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/registro-por-convite/spec.md, .spec/features/registro-por-convite/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-068 — "Testes de integração backend"
  critérios/refs: AC-155 (Resposta do convite inclui link), AC-156 (Token armazenado como hash), AC-157 (Token expira em 7 dias), AC-158 (Convite de usuário já registrado não gera link), AC-159 (Validação de token retorna dados do convite), AC-160 (Token inválido é rejeitado na validação), AC-161 (Aceitar convite cria conta e ativa vínculo), AC-162 (Token consumido não pode ser reutilizado), AC-163 (Aceitar convite ativa outros convites PENDING do mesmo email), AC-164 (Rejeitar aceitação se email já registrado), AC-167 (Endpoint POST /auth/register removido)
  arquivos permitidos (e seus testes): server/src/test/java/com/example/carboncalculator/InviteRegistrationIntegrationTest.java
  mensagem de commit: "T-068 registro-por-convite: Testes de integração backend"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' high >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-068 registro-por-convite: Testes de integração backend (auto-commit do plano)'
    fi
    marcar_concluidas T-068
    verde "✔ T-068 concluída"
    return 0
  fi
  vermelho "✘ T-068 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/registro-por-convite/executar-tarefas.sh --seq T-068"
  FALHAS="$FALHAS T-068"
  return 1
}

# ── sequencial T-069 (ordem do tasks.md) ──
executar_seq_T_069() {
  info 'sequencial T-069 — Testes frontend'
  if rodar_tarefa seq 'T-069' 'Você executa UMA tarefa da feature "registro-por-convite" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/registro-por-convite/spec.md, .spec/features/registro-por-convite/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-069 — "Testes frontend"
  critérios/refs: AC-165 (Formulário de registro exibe email readonly), AC-166 (Página de registro sem token mostra aviso), AC-168 (Página de login sem link de registro), AC-169 (Dialog exibe link copiável após convite), AC-170 (Botão copiar funciona)
  arquivos permitidos (e seus testes): client/src/pages/auth/RegisterPage.test.ts, client/src/pages/auth/LoginPage.test.ts, client/src/pages/users/UsersPage.test.ts
  mensagem de commit: "T-069 registro-por-convite: Testes frontend"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `bash .spec/scripts/run-all-tests.sh` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'claude-sonnet-5' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-069 registro-por-convite: Testes frontend (auto-commit do plano)'
    fi
    marcar_concluidas T-069
    verde "✔ T-069 concluída"
    return 0
  fi
  vermelho "✘ T-069 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/registro-por-convite/executar-tarefas.sh --seq T-069"
  FALHAS="$FALHAS T-069"
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
      amarelo "  para o veredito: bash .spec/features/registro-por-convite/executar-tarefas.sh --gate"
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
  executar_seq_T_060 || true
  executar_seq_T_061 || true
  executar_seq_T_062 || true
  executar_seq_T_063 || true
  executar_seq_T_064 || true
  executar_seq_T_065 || true
  executar_seq_T_066 || true
  executar_seq_T_067 || true
  executar_seq_T_068 || true
  executar_seq_T_069 || true
  encerrar tudo
}

listar() {
  echo "execução: $RUN_ID (feature $FEATURE, branch $BASE_BRANCH)"
  echo "  seq       T-060 (sequencial)"
  echo "  seq       T-061 (sequencial)"
  echo "  seq       T-062 (sequencial)"
  echo "  seq       T-063 (sequencial)"
  echo "  seq       T-064 (sequencial)"
  echo "  seq       T-065 (sequencial)"
  echo "  seq       T-066 (sequencial)"
  echo "  seq       T-067 (sequencial)"
  echo "  seq       T-068 (sequencial)"
  echo "  seq       T-069 (sequencial)"
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
      T-060) evento --tipo inicio --escopo "seq:T-060"; iniciar_resumos; executar_seq_T_060 || true; encerrar "seq:T-060" ;;
      T-061) evento --tipo inicio --escopo "seq:T-061"; iniciar_resumos; executar_seq_T_061 || true; encerrar "seq:T-061" ;;
      T-062) evento --tipo inicio --escopo "seq:T-062"; iniciar_resumos; executar_seq_T_062 || true; encerrar "seq:T-062" ;;
      T-063) evento --tipo inicio --escopo "seq:T-063"; iniciar_resumos; executar_seq_T_063 || true; encerrar "seq:T-063" ;;
      T-064) evento --tipo inicio --escopo "seq:T-064"; iniciar_resumos; executar_seq_T_064 || true; encerrar "seq:T-064" ;;
      T-065) evento --tipo inicio --escopo "seq:T-065"; iniciar_resumos; executar_seq_T_065 || true; encerrar "seq:T-065" ;;
      T-066) evento --tipo inicio --escopo "seq:T-066"; iniciar_resumos; executar_seq_T_066 || true; encerrar "seq:T-066" ;;
      T-067) evento --tipo inicio --escopo "seq:T-067"; iniciar_resumos; executar_seq_T_067 || true; encerrar "seq:T-067" ;;
      T-068) evento --tipo inicio --escopo "seq:T-068"; iniciar_resumos; executar_seq_T_068 || true; encerrar "seq:T-068" ;;
      T-069) evento --tipo inicio --escopo "seq:T-069"; iniciar_resumos; executar_seq_T_069 || true; encerrar "seq:T-069" ;;
      *) falhar "tarefa sequencial desconhecida: '$ALVO' — veja as disponíveis com --listar" ;;
    esac ;;
esac
