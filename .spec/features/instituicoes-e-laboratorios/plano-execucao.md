# Plano de execução — instituicoes-e-laboratorios

> gerado por `onp-spec plano` em 2026-09-18 17:46 — NÃO edite à mão;
> mudou tasks.md ou a config? Regenere: `onp-spec plano instituicoes-e-laboratorios`

## Resumo — o que vai acontecer

- **14 tarefa(s) pendente(s)**: 14 em 14 faixa(s) paralela(s) + 0 sequencial(is)
- **1 faixa = 1 worktree + 1 branch + 1 janela de contexto limpa** — faixas não compartilham nenhum arquivo entre si
- prefere outra seleção ou uma após a outra? Regenere com `onp-spec plano instituicoes-e-laboratorios --paralelizar T-xxx,T-yyy` ou `--sequencial`
- tudo acontece na branch de trabalho `spec/instituicoes-e-laboratorios`; levar para a main é decisão sua

## Faixas e ondas

### Onda 1 — faixa-1 ∥ faixa-2 ∥ faixa-3

#### faixa-1 — branch `spec/instituicoes-e-laboratorios-faixa-1` — worktree `../onp-worktrees/CarbonCalculatorTCC-instituicoes-e-laboratorios-faixa-1`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-001 | Migrations Flyway (tabelas + RLS) | `claude-sonnet-5` | medium | `server/src/main/resources/db/migration/V1__create_institution_table.sql`, `server/src/main/resources/db/migration/V2__create_laboratory_table.sql`, `server/src/main/resources/db/migration/V3__enable_rls_laboratory.sql` |

#### faixa-2 — branch `spec/instituicoes-e-laboratorios-faixa-2` — worktree `../onp-worktrees/CarbonCalculatorTCC-instituicoes-e-laboratorios-faixa-2`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-002 | Entidades JPA | `claude-sonnet-5` | medium | `server/src/main/java/com/example/carboncalculator/entities/Institution.java`, `server/src/main/java/com/example/carboncalculator/entities/Laboratory.java` |

#### faixa-3 — branch `spec/instituicoes-e-laboratorios-faixa-3` — worktree `../onp-worktrees/CarbonCalculatorTCC-instituicoes-e-laboratorios-faixa-3`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-003 | Tenant filter (servlet filter para RLS) | `claude-sonnet-5` | medium | `server/src/main/java/com/example/carboncalculator/config/TenantFilter.java`, `server/src/main/java/com/example/carboncalculator/config/TenantContext.java` |

### Onda 2 — faixa-4 ∥ faixa-5 ∥ faixa-6

#### faixa-4 — branch `spec/instituicoes-e-laboratorios-faixa-4` — worktree `../onp-worktrees/CarbonCalculatorTCC-instituicoes-e-laboratorios-faixa-4`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-004 | Repositories | `claude-sonnet-5` | medium | `server/src/main/java/com/example/carboncalculator/repositories/InstitutionRepository.java`, `server/src/main/java/com/example/carboncalculator/repositories/LaboratoryRepository.java` |

#### faixa-5 — branch `spec/instituicoes-e-laboratorios-faixa-5` — worktree `../onp-worktrees/CarbonCalculatorTCC-instituicoes-e-laboratorios-faixa-5`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-005 | DTOs e Mappers | `claude-sonnet-5` | medium | `server/src/main/java/com/example/carboncalculator/dto/CreateInstitutionRequest.java`, `server/src/main/java/com/example/carboncalculator/dto/InstitutionResponse.java`, `server/src/main/java/com/example/carboncalculator/dto/CreateLaboratoryRequest.java`, `server/src/main/java/com/example/carboncalculator/dto/LaboratoryResponse.java`, `server/src/main/java/com/example/carboncalculator/dto/UpdateInstitutionRequest.java`, `server/src/main/java/com/example/carboncalculator/dto/UpdateLaboratoryRequest.java`, `server/src/main/java/com/example/carboncalculator/mappers/InstitutionMapper.java`, `server/src/main/java/com/example/carboncalculator/mappers/LaboratoryMapper.java` |

#### faixa-6 — branch `spec/instituicoes-e-laboratorios-faixa-6` — worktree `../onp-worktrees/CarbonCalculatorTCC-instituicoes-e-laboratorios-faixa-6`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-006 | Services | `claude-sonnet-5` | high | `server/src/main/java/com/example/carboncalculator/services/InstitutionService.java`, `server/src/main/java/com/example/carboncalculator/services/LaboratoryService.java` |

### Onda 3 — faixa-7 ∥ faixa-8 ∥ faixa-9

#### faixa-7 — branch `spec/instituicoes-e-laboratorios-faixa-7` — worktree `../onp-worktrees/CarbonCalculatorTCC-instituicoes-e-laboratorios-faixa-7`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-007 | Controllers REST | `claude-sonnet-5` | medium | `server/src/main/java/com/example/carboncalculator/controllers/InstitutionController.java`, `server/src/main/java/com/example/carboncalculator/controllers/LaboratoryController.java` |

#### faixa-8 — branch `spec/instituicoes-e-laboratorios-faixa-8` — worktree `../onp-worktrees/CarbonCalculatorTCC-instituicoes-e-laboratorios-faixa-8`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-008 | Setup frontend (Biome, Zod, RHF, Router, HTTP client) | `claude-sonnet-5` | medium | `client/package.json`, `client/biome.json`, `client/src/lib/api/client.ts`, `client/src/lib/api/institutions.ts`, `client/src/lib/api/laboratories.ts`, `client/src/main.tsx` |

#### faixa-9 — branch `spec/instituicoes-e-laboratorios-faixa-9` — worktree `../onp-worktrees/CarbonCalculatorTCC-instituicoes-e-laboratorios-faixa-9`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-009 | Contexto de instituição e layout | `claude-sonnet-5` | medium | `client/src/context/InstitutionContext.tsx`, `client/src/layout/AppLayout.tsx`, `client/src/layout/InstitutionSwitcher.tsx` |

### Onda 4 — faixa-10 ∥ faixa-11 ∥ faixa-12

#### faixa-10 — branch `spec/instituicoes-e-laboratorios-faixa-10` — worktree `../onp-worktrees/CarbonCalculatorTCC-instituicoes-e-laboratorios-faixa-10`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-010 | Formulário de instituição | `claude-sonnet-5` | medium | `client/src/pages/institutions/InstitutionForm.tsx`, `client/src/lib/schemas/institutionSchema.ts` |

#### faixa-11 — branch `spec/instituicoes-e-laboratorios-faixa-11` — worktree `../onp-worktrees/CarbonCalculatorTCC-instituicoes-e-laboratorios-faixa-11`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-011 | Lista de laboratórios | `claude-sonnet-5` | medium | `client/src/pages/laboratories/LaboratoryList.tsx` |

#### faixa-12 — branch `spec/instituicoes-e-laboratorios-faixa-12` — worktree `../onp-worktrees/CarbonCalculatorTCC-instituicoes-e-laboratorios-faixa-12`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-012 | Formulário de laboratório e desativação | `claude-sonnet-5` | medium | `client/src/pages/laboratories/LaboratoryForm.tsx`, `client/src/pages/laboratories/DeactivateDialog.tsx`, `client/src/lib/schemas/laboratorySchema.ts` |

### Onda 5 — faixa-13 ∥ faixa-14

#### faixa-13 — branch `spec/instituicoes-e-laboratorios-faixa-13` — worktree `../onp-worktrees/CarbonCalculatorTCC-instituicoes-e-laboratorios-faixa-13`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-013 | Testes de integração (backend + RLS) | `claude-sonnet-5` | high | `server/src/test/java/com/example/carboncalculator/controllers/InstitutionControllerIntegrationTest.java`, `server/src/test/java/com/example/carboncalculator/controllers/LaboratoryControllerIntegrationTest.java` |

#### faixa-14 — branch `spec/instituicoes-e-laboratorios-faixa-14` — worktree `../onp-worktrees/CarbonCalculatorTCC-instituicoes-e-laboratorios-faixa-14`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-014 | Testes unitários (services) | `claude-sonnet-5` | medium | `server/src/test/java/com/example/carboncalculator/services/InstitutionServiceTest.java`, `server/src/test/java/com/example/carboncalculator/services/LaboratoryServiceTest.java` |

## Gestão de branches e commits

1. branch de trabalho `spec/instituicoes-e-laboratorios` criada do ponto atual (se ainda não existir)
2. cada faixa nasce dela como branch própria e roda no seu worktree — **1 tarefa = 1 commit** (`T-xxx feature: título`)
3. terminou a onda → merge `--no-ff` de cada faixa de volta, na ordem; conflito interrompe a faixa e pede resolução humana
4. faixa mesclada → worktree removido, branch apagada, tarefa marcada `[concluida]` no tasks.md
5. gate final na branch de trabalho: `onp-spec verify instituicoes-e-laboratorios` + `onp-spec audit --ci` — **exit 0 ou não está pronto**

## Como executar

### ▶ Execução — Claude Code headless

```bash
bash .spec/features/instituicoes-e-laboratorios/executar-tarefas.sh
```

Cada faixa roda `claude -p` com **janela de contexto limpa**, no seu worktree, com
`--model` e `--effort` já definidos por tarefa e permissões `acceptEdits`. Os prompts exatos estão
embutidos no script — quer rodar uma faixa na mão, é só copiá-los de lá.
Logs: `../onp-worktrees/CarbonCalculatorTCC-instituicoes-e-laboratorios-logs/`.

### 📣 Acompanhamento — tabela + resumo no chat (a cada 1 min)

O script roda em **background**: o agente AVISA o usuário antes de iniciar e,
enquanto roda, posta no chat a cada ~1 minuto a **tabela de andamento** (qual
tarefa está rodando, qual não está, o que concluiu/falhou) junto com o
**resumo geral de andamento** (escrito por IA; sem IA, o motor resume). Ao
final, o usuário recebe o resumo completo da execução. A qualquer momento:

```bash
onp-spec resumo instituicoes-e-laboratorios --tabela   # a tabela de andamento
onp-spec resumo instituicoes-e-laboratorios            # o resumo em texto
```

