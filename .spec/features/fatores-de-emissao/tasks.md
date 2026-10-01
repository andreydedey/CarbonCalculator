# Tasks: Fatores de Emissão

> feature: fatores-de-emissao

## T-051 — Testes de integração (EmissionFactorControllerIntegrationTest) [pendente]
- Refs: US-033, US-034, AC-103, AC-104, AC-105, AC-106, AC-107, AC-108, AC-109, AC-110, AC-111, AC-112, AC-113, AC-114, AC-115
- Arquivos: server/src/test/java/com/example/carboncalculator/controllers/EmissionFactorControllerIntegrationTest.java
- Esforço: alto
- Notas: Testcontainers + PostgreSQL real (RLS). Cobre CRUD completo, validações (400/409/404), controle de acesso (403 para MANAGER), isolamento de instituições e filtro por ano. Código backend já existente (EmissionFactorController + Service + V18).

## T-052 — Testes unitários (EmissionFactorServiceTest) [pendente]
- Refs: US-033, AC-104, AC-105, AC-106, AC-107, AC-109, AC-111
- Arquivos: server/src/test/java/com/example/carboncalculator/services/EmissionFactorServiceTest.java
- Esforço: baixo
- Notas: Mock de EmissionFactorRepository e InstitutionRepository. Testa cada validação do service: mês nulo, valor <= 0, fonte em branco, duplicata na criação e na atualização, not found no delete.

## T-053 — Frontend: computeEmissionFactorRows + testes [pendente]
- Refs: US-035, AC-116, AC-117, AC-118
- Arquivos: client/src/lib/utils/emission-factor-rows.ts, client/src/lib/utils/emission-factor-rows.test.ts
- Esforço: baixo
- Notas: Extrair lógica de status em função pura `computeEmissionFactorRows(factors, currentDate, year)`. Testa com Node.js test runner: Pendente para meses sem fator ≤ hoje, Em uso para o mais recente ≤ hoje, Anterior para os demais.

## T-054 — Frontend: integrar detecção de lacunas na EmissionFactorsPage [pendente]
- Refs: US-035, AC-116, AC-117, AC-118
- Arquivos: client/src/pages/emission-factors/EmissionFactorsPage.tsx
- Esforço: medio
- Notas: Substituir tabela atual (mostra só fatores existentes) pela tabela com linhas Pendente + badges de status (Em uso verde / Anterior cinza / Pendente âmbar). Botão "Cadastrar" na linha Pendente pré-preenche o formulário com aquele mês. Depende de T-053.
