# Plano de execução — acesso-e-papeis

> gerado por `onp-spec plano` em 2026-09-21 02:58 — NÃO edite à mão;
> mudou tasks.md ou a config? Regenere: `onp-spec plano acesso-e-papeis --paralelizar T-010,T-011,T-012,T-015,T-016,T-017,T-018`

## Resumo — o que vai acontecer

- **15 tarefa(s) pendente(s)**: 7 em 7 faixa(s) paralela(s) + 8 sequencial(is)
- **seleção do usuário**: paralelizar só T-010, T-011, T-012, T-015, T-016, T-017, T-018 — as demais rodam uma após a outra, ao final
- **1 faixa = 1 worktree + 1 branch + 1 janela de contexto limpa** — faixas não compartilham nenhum arquivo entre si
- prefere outra seleção ou uma após a outra? Regenere com `onp-spec plano acesso-e-papeis --paralelizar T-xxx,T-yyy` ou `--sequencial`
- tudo acontece na branch de trabalho `spec/acesso-e-papeis`; levar para a main é decisão sua

## Faixas e ondas

### Onda 1 — faixa-1 ∥ faixa-2 ∥ faixa-3

#### faixa-1 — branch `spec/acesso-e-papeis-faixa-1` — worktree `../onp-worktrees/CarbonCalculatorTCC-acesso-e-papeis-faixa-1`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-010 | Google OAuth2 | `claude-sonnet-5` | high | `server/pom.xml`, `server/src/main/java/com/example/carboncalculator/security/OAuth2SuccessHandler.java`, `server/src/main/resources/application.yaml` |

#### faixa-2 — branch `spec/acesso-e-papeis-faixa-2` — worktree `../onp-worktrees/CarbonCalculatorTCC-acesso-e-papeis-faixa-2`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-011 | TenantFilter: validar vínculo user↔institution | `claude-sonnet-5` | medium | `server/src/main/java/com/example/carboncalculator/config/TenantFilter.java` |

#### faixa-3 — branch `spec/acesso-e-papeis-faixa-3` — worktree `../onp-worktrees/CarbonCalculatorTCC-acesso-e-papeis-faixa-3`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-012 | @PreAuthorize em controllers existentes | `claude-sonnet-5` | low | `server/src/main/java/com/example/carboncalculator/controllers/InstitutionController.java`, `server/src/main/java/com/example/carboncalculator/controllers/LaboratoryController.java` |

### Onda 2 — faixa-4 ∥ faixa-5 ∥ faixa-6

#### faixa-4 — branch `spec/acesso-e-papeis-faixa-4` — worktree `../onp-worktrees/CarbonCalculatorTCC-acesso-e-papeis-faixa-4`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-015 | Frontend: página de login | `claude-sonnet-5` | medium | `client/src/pages/auth/LoginPage.tsx`, `client/src/lib/schemas/loginSchema.ts` |

#### faixa-5 — branch `spec/acesso-e-papeis-faixa-5` — worktree `../onp-worktrees/CarbonCalculatorTCC-acesso-e-papeis-faixa-5`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-016 | Frontend: página de registro | `claude-sonnet-5` | medium | `client/src/pages/auth/RegisterPage.tsx`, `client/src/lib/schemas/registerSchema.ts` |

#### faixa-6 — branch `spec/acesso-e-papeis-faixa-6` — worktree `../onp-worktrees/CarbonCalculatorTCC-acesso-e-papeis-faixa-6`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-017 | Frontend: página de gestão de usuários | `claude-sonnet-5` | high | `client/src/pages/users/UsersPage.tsx`, `client/src/components/users/UsersTable.tsx`, `client/src/components/users/InviteDialog.tsx`, `client/src/lib/api/users.ts`, `client/src/lib/schemas/inviteSchema.ts` |

### Onda 3 — faixa-7

#### faixa-7 — branch `spec/acesso-e-papeis-faixa-7` — worktree `../onp-worktrees/CarbonCalculatorTCC-acesso-e-papeis-faixa-7`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-018 | Frontend: página admin de instituições | `claude-sonnet-5` | medium | `client/src/pages/admin/AdminInstitutionsPage.tsx`, `client/src/components/admin/InstitutionCard.tsx` |

## Tarefas sequenciais (após as ondas, na árvore principal)

| tarefa | título | modelo | esforço | por que sequencial |
|---|---|---|---|---|
| T-006 | Migrations e entidades JPA (app_user, user_institution, public_results) | `claude-sonnet-5` | medium | fora da seleção do usuário |
| T-007 | JWT service (geração, validação, refresh) | `claude-sonnet-5` | high | fora da seleção do usuário |
| T-008 | SecurityFilterChain e role hierarchy | `claude-sonnet-5` | medium | fora da seleção do usuário |
| T-009 | AuthController (register, login, refresh, me) | `claude-sonnet-5` | high | fora da seleção do usuário |
| T-013 | UserController e UserService (gestão de membros) | `claude-sonnet-5` | high | fora da seleção do usuário |
| T-014 | Frontend: AuthContext, interceptor e rotas protegidas | `claude-sonnet-5` | high | fora da seleção do usuário |
| T-019 | Frontend: atualizações no layout (sidebar, topbar, institution switcher) | `claude-sonnet-5` | medium | fora da seleção do usuário |
| T-020 | Testes de integração (auth + autorização + cross-tenant) | `claude-sonnet-5` | high | fora da seleção do usuário |

## Gestão de branches e commits

1. branch de trabalho `spec/acesso-e-papeis` criada do ponto atual (se ainda não existir)
2. cada faixa nasce dela como branch própria e roda no seu worktree — **1 tarefa = 1 commit** (`T-xxx feature: título`)
3. terminou a onda → merge `--no-ff` de cada faixa de volta, na ordem; conflito interrompe a faixa e pede resolução humana
4. faixa mesclada → worktree removido, branch apagada, tarefa marcada `[concluida]` no tasks.md
5. gate final na branch de trabalho: `onp-spec verify acesso-e-papeis` + `onp-spec audit --ci` — **exit 0 ou não está pronto**

## Como executar

### ▶ Execução — Claude Code headless

```bash
bash .spec/features/acesso-e-papeis/executar-tarefas.sh
```

Cada faixa roda `claude -p` com **janela de contexto limpa**, no seu worktree, com
`--model` e `--effort` já definidos por tarefa e permissões `acceptEdits`. Os prompts exatos estão
embutidos no script — quer rodar uma faixa na mão, é só copiá-los de lá.
Logs: `../onp-worktrees/CarbonCalculatorTCC-acesso-e-papeis-logs/`.

### 📣 Acompanhamento — tabela + resumo no chat (a cada 1 min)

O script roda em **background**: o agente AVISA o usuário antes de iniciar e,
enquanto roda, posta no chat a cada ~1 minuto a **tabela de andamento** (qual
tarefa está rodando, qual não está, o que concluiu/falhou) junto com o
**resumo geral de andamento** (escrito por IA; sem IA, o motor resume). Ao
final, o usuário recebe o resumo completo da execução. A qualquer momento:

```bash
onp-spec resumo acesso-e-papeis --tabela   # a tabela de andamento
onp-spec resumo acesso-e-papeis            # o resumo em texto
```

