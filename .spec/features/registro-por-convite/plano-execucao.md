# Plano de execução — registro-por-convite

> gerado por `onp-spec plano` em 2026-10-08 15:02 — NÃO edite à mão;
> mudou tasks.md ou a config? Regenere: `onp-spec plano registro-por-convite --sequencial`

## Resumo — o que vai acontecer

- **modo SEQUENCIAL (escolha do usuário)**: 10 tarefa(s) pendente(s), UMA APÓS A OUTRA, na árvore principal
- sem worktrees e sem paralelismo — cada tarefa roda numa janela de contexto limpa, na ordem do tasks.md
- tudo acontece na branch de trabalho `spec/registro-por-convite`; levar para a main é decisão sua

## Ordem de execução (uma tarefa após a outra)

| tarefa | título | modelo | esforço |
|---|---|---|---|
| T-060 | Migration: colunas de token no user_institution | `claude-sonnet-5` | medium |
| T-061 | Backend: InviteTokenService + alterar UserInstitution entity | `claude-sonnet-5` | medium |
| T-062 | Backend: alterar UserService.invite() para gerar token | `claude-sonnet-5` | medium |
| T-063 | Backend: endpoints de validação e aceitação de convite | `claude-sonnet-5` | medium |
| T-064 | Backend: remover endpoint POST /auth/register | `claude-sonnet-5` | medium |
| T-065 | Frontend: refatorar RegisterPage para exigir token | `claude-sonnet-5` | medium |
| T-066 | Frontend: refatorar LoginPage (remover link de registro) | `claude-sonnet-5` | medium |
| T-067 | Frontend: dialog de link de convite na UsersPage | `claude-sonnet-5` | medium |
| T-068 | Testes de integração backend | `claude-sonnet-5` | high |
| T-069 | Testes frontend | `claude-sonnet-5` | medium |

## Gestão de branches e commits

1. branch de trabalho `spec/registro-por-convite` criada do ponto atual (se ainda não existir)
2. as tarefas rodam nela mesma, na ordem — **1 tarefa = 1 commit** (`T-xxx feature: título`), marcada `[concluida]` só com trabalho feito
3. gate final na branch de trabalho: `onp-spec verify registro-por-convite` + `onp-spec audit --ci` — **exit 0 ou não está pronto**

## Como executar

### ▶ Execução — Claude Code headless

```bash
bash .spec/features/registro-por-convite/executar-tarefas.sh
```

Cada tarefa roda `claude -p` com **janela de contexto limpa**, na árvore principal,
uma após a outra, com `--model` e `--effort` já definidos por tarefa e permissões `acceptEdits`.
Os prompts exatos estão embutidos no script.
Logs: `../onp-worktrees/CarbonCalculatorTCC-registro-por-convite-logs/`.

### 📣 Acompanhamento — tabela + resumo no chat (a cada 1 min)

O script roda em **background**: o agente AVISA o usuário antes de iniciar e,
enquanto roda, posta no chat a cada ~1 minuto a **tabela de andamento** (qual
tarefa está rodando, qual não está, o que concluiu/falhou) junto com o
**resumo geral de andamento** (escrito por IA; sem IA, o motor resume). Ao
final, o usuário recebe o resumo completo da execução. A qualquer momento:

```bash
onp-spec resumo registro-por-convite --tabela   # a tabela de andamento
onp-spec resumo registro-por-convite            # o resumo em texto
```

