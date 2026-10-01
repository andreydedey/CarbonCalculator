# Plano de execução — calculo-de-emissoes

> gerado por `onp-spec plano` em 2026-09-30 00:39 — NÃO edite à mão;
> mudou tasks.md ou a config? Regenere: `onp-spec plano calculo-de-emissoes`

## Resumo — o que vai acontecer

- **10 tarefa(s) pendente(s)**: 10 em 10 faixa(s) paralela(s) + 0 sequencial(is)
- **1 faixa = 1 worktree + 1 branch + 1 janela de contexto limpa** — faixas não compartilham nenhum arquivo entre si
- prefere outra seleção ou uma após a outra? Regenere com `onp-spec plano calculo-de-emissoes --paralelizar T-xxx,T-yyy` ou `--sequencial`
- tudo acontece na branch de trabalho `spec/calculo-de-emissoes`; levar para a main é decisão sua

## Faixas e ondas

### Onda 1 — faixa-1 ∥ faixa-2 ∥ faixa-3

#### faixa-1 — branch `spec/calculo-de-emissoes-faixa-1` — worktree `../onp-worktrees/CarbonCalculatorTCC-calculo-de-emissoes-faixa-1`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-041 | Migration e entity EmissionFactor | `claude-sonnet-5` | medium | `server/src/main/resources/db/migration/V16__create_emission_factor_table.sql`, `server/src/main/java/com/example/carboncalculator/entities/EmissionFactor.java` |

#### faixa-2 — branch `spec/calculo-de-emissoes-faixa-2` — worktree `../onp-worktrees/CarbonCalculatorTCC-calculo-de-emissoes-faixa-2`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-042 | EmissionFactorRepository + Service + Controller (CRUD) | `claude-sonnet-5` | medium | `server/src/main/java/com/example/carboncalculator/repositories/EmissionFactorRepository.java`, `server/src/main/java/com/example/carboncalculator/services/EmissionFactorService.java`, `server/src/main/java/com/example/carboncalculator/controllers/EmissionFactorController.java`, `server/src/main/java/com/example/carboncalculator/dtos/EmissionFactorDTO.java`, `server/src/main/java/com/example/carboncalculator/dtos/CreateEmissionFactorRequest.java` |

#### faixa-3 — branch `spec/calculo-de-emissoes-faixa-3` — worktree `../onp-worktrees/CarbonCalculatorTCC-calculo-de-emissoes-faixa-3`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-043 | EmissionCalculationService (motor de cálculo) | `claude-sonnet-5` | high | `server/src/main/java/com/example/carboncalculator/services/EmissionCalculationService.java`, `server/src/main/java/com/example/carboncalculator/dtos/EmissionResultDTO.java` |

### Onda 2 — faixa-4 ∥ faixa-5 ∥ faixa-6

#### faixa-4 — branch `spec/calculo-de-emissoes-faixa-4` — worktree `../onp-worktrees/CarbonCalculatorTCC-calculo-de-emissoes-faixa-4`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-044 | EmissionController (cálculo, readiness, export) | `claude-sonnet-5` | medium | `server/src/main/java/com/example/carboncalculator/controllers/EmissionController.java`, `server/src/main/java/com/example/carboncalculator/dtos/ReadinessDTO.java` |

#### faixa-5 — branch `spec/calculo-de-emissoes-faixa-5` — worktree `../onp-worktrees/CarbonCalculatorTCC-calculo-de-emissoes-faixa-5`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-045 | Testes de integração do backend (fatores + cálculo) | `claude-sonnet-5` | high | `server/src/test/java/com/example/carboncalculator/EmissionFactorIntegrationTest.java`, `server/src/test/java/com/example/carboncalculator/EmissionCalculationIntegrationTest.java` |

#### faixa-6 — branch `spec/calculo-de-emissoes-faixa-6` — worktree `../onp-worktrees/CarbonCalculatorTCC-calculo-de-emissoes-faixa-6`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-046 | API client frontend (emission-factors + emissions) | `claude-sonnet-5` | medium | `client/src/lib/api/emission-factors.ts`, `client/src/lib/api/emissions.ts` |

### Onda 3 — faixa-7 ∥ faixa-8 ∥ faixa-9

#### faixa-7 — branch `spec/calculo-de-emissoes-faixa-7` — worktree `../onp-worktrees/CarbonCalculatorTCC-calculo-de-emissoes-faixa-7`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-047 | Sidebar + rotas (emissões e fatores) | `claude-sonnet-5` | medium | `client/src/components/layout/AppLayout.tsx`, `client/src/App.tsx` |

#### faixa-8 — branch `spec/calculo-de-emissoes-faixa-8` — worktree `../onp-worktrees/CarbonCalculatorTCC-calculo-de-emissoes-faixa-8`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-048 | EmissionFactorsPage (CRUD frontend) | `claude-sonnet-5` | medium | `client/src/pages/emission-factors/EmissionFactorsPage.tsx`, `client/src/lib/schemas/emissionFactorSchema.ts` |

#### faixa-9 — branch `spec/calculo-de-emissoes-faixa-9` — worktree `../onp-worktrees/CarbonCalculatorTCC-calculo-de-emissoes-faixa-9`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-049 | EmissionsDashboard (página principal de emissões) | `claude-sonnet-5` | high | `client/src/pages/emissions/EmissionsDashboardPage.tsx`, `client/src/components/emissions/EmissionsByMonth.tsx`, `client/src/components/emissions/EmissionsByLab.tsx`, `client/src/components/emissions/BreakdownCards.tsx`, `client/src/components/emissions/EquivalenceCards.tsx`, `client/src/components/emissions/TransparencyPanel.tsx`, `client/src/components/emissions/ReadinessCheck.tsx`, `client/src/components/emissions/ExportButton.tsx` |

### Onda 4 — faixa-10

#### faixa-10 — branch `spec/calculo-de-emissoes-faixa-10` — worktree `../onp-worktrees/CarbonCalculatorTCC-calculo-de-emissoes-faixa-10`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-050 | Seed de fatores de emissão | `claude-sonnet-5` | medium | `server/src/main/resources/db/migration/afterMigrate.sql` |

## Gestão de branches e commits

1. branch de trabalho `spec/calculo-de-emissoes` criada do ponto atual (se ainda não existir)
2. cada faixa nasce dela como branch própria e roda no seu worktree — **1 tarefa = 1 commit** (`T-xxx feature: título`)
3. terminou a onda → merge `--no-ff` de cada faixa de volta, na ordem; conflito interrompe a faixa e pede resolução humana
4. faixa mesclada → worktree removido, branch apagada, tarefa marcada `[concluida]` no tasks.md
5. gate final na branch de trabalho: `onp-spec verify calculo-de-emissoes` + `onp-spec audit --ci` — **exit 0 ou não está pronto**

## Como executar

### ▶ Execução — Claude Code headless

```bash
bash .spec/features/calculo-de-emissoes/executar-tarefas.sh
```

Cada faixa roda `claude -p` com **janela de contexto limpa**, no seu worktree, com
`--model` e `--effort` já definidos por tarefa e permissões `acceptEdits`. Os prompts exatos estão
embutidos no script — quer rodar uma faixa na mão, é só copiá-los de lá.
Logs: `../onp-worktrees/CarbonCalculatorTCC-calculo-de-emissoes-logs/`.

### 📣 Acompanhamento — tabela + resumo no chat (a cada 1 min)

O script roda em **background**: o agente AVISA o usuário antes de iniciar e,
enquanto roda, posta no chat a cada ~1 minuto a **tabela de andamento** (qual
tarefa está rodando, qual não está, o que concluiu/falhou) junto com o
**resumo geral de andamento** (escrito por IA; sem IA, o motor resume). Ao
final, o usuário recebe o resumo completo da execução. A qualquer momento:

```bash
onp-spec resumo calculo-de-emissoes --tabela   # a tabela de andamento
onp-spec resumo calculo-de-emissoes            # o resumo em texto
```

