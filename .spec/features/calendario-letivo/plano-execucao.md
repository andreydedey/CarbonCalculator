# Plano de execução — calendario-letivo

> gerado por `onp-spec plano` em 2026-09-24 23:52 — NÃO edite à mão;
> mudou tasks.md ou a config? Regenere: `onp-spec plano calendario-letivo --sequencial`

## Resumo — o que vai acontecer

- **modo SEQUENCIAL (escolha do usuário)**: 12 tarefa(s) pendente(s), UMA APÓS A OUTRA, na árvore principal
- sem worktrees e sem paralelismo — cada tarefa roda numa janela de contexto limpa, na ordem do tasks.md
- tudo acontece na branch de trabalho `spec/calendario-letivo`; levar para a main é decisão sua

## Ordem de execução (uma tarefa após a outra)

| tarefa | título | modelo | esforço |
|---|---|---|---|
| T-014 | Migrations e entidades JPA | `claude-sonnet-5` | medium |
| T-015 | Repositories e DTOs | `claude-sonnet-5` | medium |
| T-016 | AcademicPeriodService com validações | `claude-sonnet-5` | high |
| T-017 | LaboratoryScheduleService com validações | `claude-sonnet-5` | medium |
| T-018 | PeriodSummaryService (cálculos derivados) | `claude-sonnet-5` | high |
| T-019 | Controllers REST | `claude-sonnet-5` | medium |
| T-020 | GlobalExceptionHandler — novas exceções | `claude-sonnet-5` | low |
| T-021 | Seed data para calendário | `claude-sonnet-5` | low |
| T-022 | Frontend: API client e tipos | `claude-sonnet-5` | low |
| T-023 | Frontend: listagem e CRUD de períodos | `claude-sonnet-5` | medium |
| T-024 | Frontend: detalhe do período (feriados + grades + resumo) | `claude-sonnet-5` | high |
| T-025 | Frontend: sidebar, rotas e cópia de período | `claude-sonnet-5` | low |

## Gestão de branches e commits

1. branch de trabalho `spec/calendario-letivo` criada do ponto atual (se ainda não existir)
2. as tarefas rodam nela mesma, na ordem — **1 tarefa = 1 commit** (`T-xxx feature: título`), marcada `[concluida]` só com trabalho feito
3. gate final na branch de trabalho: `onp-spec verify calendario-letivo` + `onp-spec audit --ci` — **exit 0 ou não está pronto**

## Como executar

### ▶ Execução — Claude Code headless

```bash
bash .spec/features/calendario-letivo/executar-tarefas.sh
```

Cada tarefa roda `claude -p` com **janela de contexto limpa**, na árvore principal,
uma após a outra, com `--model` e `--effort` já definidos por tarefa e permissões `acceptEdits`.
Os prompts exatos estão embutidos no script.
Logs: `../onp-worktrees/CarbonCalculatorTCC-calendario-letivo-logs/`.

### 📣 Acompanhamento — tabela + resumo no chat (a cada 1 min)

O script roda em **background**: o agente AVISA o usuário antes de iniciar e,
enquanto roda, posta no chat a cada ~1 minuto a **tabela de andamento** (qual
tarefa está rodando, qual não está, o que concluiu/falhou) junto com o
**resumo geral de andamento** (escrito por IA; sem IA, o motor resume). Ao
final, o usuário recebe o resumo completo da execução. A qualquer momento:

```bash
onp-spec resumo calendario-letivo --tabela   # a tabela de andamento
onp-spec resumo calendario-letivo            # o resumo em texto
```

