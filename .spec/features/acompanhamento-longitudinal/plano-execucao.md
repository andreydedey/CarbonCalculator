# Plano de execução — acompanhamento-longitudinal

> gerado por `onp-spec plano` em 2026-10-01 19:52 — NÃO edite à mão;
> mudou tasks.md ou a config? Regenere: `onp-spec plano acompanhamento-longitudinal`

## Resumo — o que vai acontecer

- **9 tarefa(s) pendente(s)**: 9 em 9 faixa(s) paralela(s) + 0 sequencial(is)
- **1 faixa = 1 worktree + 1 branch + 1 janela de contexto limpa** — faixas não compartilham nenhum arquivo entre si
- prefere outra seleção ou uma após a outra? Regenere com `onp-spec plano acompanhamento-longitudinal --paralelizar T-xxx,T-yyy` ou `--sequencial`
- tudo acontece na branch de trabalho `spec/acompanhamento-longitudinal`; levar para a main é decisão sua

## Faixas e ondas

### Onda 1 — faixa-1 ∥ faixa-2 ∥ faixa-3

#### faixa-1 — branch `spec/acompanhamento-longitudinal-faixa-1` — worktree `../onp-worktrees/CarbonCalculatorTCC-acompanhamento-longitudinal-faixa-1`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-051 | Migration V19: tabela emission_snapshot | `claude-sonnet-5` | low | `server/src/main/resources/db/migration/V19__create_emission_snapshot_table.sql` |

#### faixa-2 — branch `spec/acompanhamento-longitudinal-faixa-2` — worktree `../onp-worktrees/CarbonCalculatorTCC-acompanhamento-longitudinal-faixa-2`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-052 | Entidade, repositório e DTO de EmissionSnapshot | `claude-sonnet-5` | low | `server/src/main/java/com/example/carboncalculator/entities/EmissionSnapshot.java`, `server/src/main/java/com/example/carboncalculator/repositories/EmissionSnapshotRepository.java`, `server/src/main/java/com/example/carboncalculator/dto/SnapshotAggregateDTO.java` |

#### faixa-3 — branch `spec/acompanhamento-longitudinal-faixa-3` — worktree `../onp-worktrees/CarbonCalculatorTCC-acompanhamento-longitudinal-faixa-3`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-053 | EmissionSnapshotCronService: captura diária automática | `claude-sonnet-5` | high | `server/src/main/java/com/example/carboncalculator/services/EmissionSnapshotCronService.java` |

### Onda 2 — faixa-4 ∥ faixa-5 ∥ faixa-6

#### faixa-4 — branch `spec/acompanhamento-longitudinal-faixa-4` — worktree `../onp-worktrees/CarbonCalculatorTCC-acompanhamento-longitudinal-faixa-4`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-054 | EmissionSnapshotQueryService: agregação por granularidade | `claude-sonnet-5` | high | `server/src/main/java/com/example/carboncalculator/services/EmissionSnapshotQueryService.java` |

#### faixa-5 — branch `spec/acompanhamento-longitudinal-faixa-5` — worktree `../onp-worktrees/CarbonCalculatorTCC-acompanhamento-longitudinal-faixa-5`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-055 | EmissionSnapshotController: GET /api/v1/snapshots | `claude-sonnet-5` | low | `server/src/main/java/com/example/carboncalculator/controllers/EmissionSnapshotController.java` |

#### faixa-6 — branch `spec/acompanhamento-longitudinal-faixa-6` — worktree `../onp-worktrees/CarbonCalculatorTCC-acompanhamento-longitudinal-faixa-6`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-056 | Testes de integração: cron e query | `claude-sonnet-5` | high | `server/src/test/java/com/example/carboncalculator/EmissionSnapshotIntegrationTest.java` |

### Onda 3 — faixa-7 ∥ faixa-8 ∥ faixa-9

#### faixa-7 — branch `spec/acompanhamento-longitudinal-faixa-7` — worktree `../onp-worktrees/CarbonCalculatorTCC-acompanhamento-longitudinal-faixa-7`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-057 | Testes unitários: EmissionSnapshotQueryService | `claude-sonnet-5` | medium | `server/src/test/java/com/example/carboncalculator/EmissionSnapshotQueryServiceTest.java` |

#### faixa-8 — branch `spec/acompanhamento-longitudinal-faixa-8` — worktree `../onp-worktrees/CarbonCalculatorTCC-acompanhamento-longitudinal-faixa-8`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-058 | LongitudinalPage: React com toggle, gráfico e tabela | `claude-sonnet-5` | high | `client/src/pages/longitudinal/LongitudinalPage.tsx`, `client/src/lib/api/snapshots.ts`, `client/src/lib/schemas/snapshotSchema.ts` |

#### faixa-9 — branch `spec/acompanhamento-longitudinal-faixa-9` — worktree `../onp-worktrees/CarbonCalculatorTCC-acompanhamento-longitudinal-faixa-9`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-059 | Testes de componente: LongitudinalPage | `claude-sonnet-5` | medium | `client/src/pages/longitudinal/LongitudinalPage.test.tsx` |

## Gestão de branches e commits

1. branch de trabalho `spec/acompanhamento-longitudinal` criada do ponto atual (se ainda não existir)
2. cada faixa nasce dela como branch própria e roda no seu worktree — **1 tarefa = 1 commit** (`T-xxx feature: título`)
3. terminou a onda → merge `--no-ff` de cada faixa de volta, na ordem; conflito interrompe a faixa e pede resolução humana
4. faixa mesclada → worktree removido, branch apagada, tarefa marcada `[concluida]` no tasks.md
5. gate final na branch de trabalho: `onp-spec verify acompanhamento-longitudinal` + `onp-spec audit --ci` — **exit 0 ou não está pronto**

## Como executar

### ▶ Execução — Claude Code headless

```bash
bash .spec/features/acompanhamento-longitudinal/executar-tarefas.sh
```

Cada faixa roda `claude -p` com **janela de contexto limpa**, no seu worktree, com
`--model` e `--effort` já definidos por tarefa e permissões `acceptEdits`. Os prompts exatos estão
embutidos no script — quer rodar uma faixa na mão, é só copiá-los de lá.
Logs: `../onp-worktrees/CarbonCalculatorTCC-acompanhamento-longitudinal-logs/`.

### 📣 Acompanhamento — tabela + resumo no chat (a cada 1 min)

O script roda em **background**: o agente AVISA o usuário antes de iniciar e,
enquanto roda, posta no chat a cada ~1 minuto a **tabela de andamento** (qual
tarefa está rodando, qual não está, o que concluiu/falhou) junto com o
**resumo geral de andamento** (escrito por IA; sem IA, o motor resume). Ao
final, o usuário recebe o resumo completo da execução. A qualquer momento:

```bash
onp-spec resumo acompanhamento-longitudinal --tabela   # a tabela de andamento
onp-spec resumo acompanhamento-longitudinal            # o resumo em texto
```

