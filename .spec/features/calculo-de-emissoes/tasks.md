# Tasks: Cálculo e apresentação das emissões

> feature: calculo-de-emissoes

## T-041 — Migration e entity EmissionFactor [concluida]
- Refs: US-026, AC-072, AC-073, AC-074
- Arquivos: server/src/main/resources/db/migration/V16__create_emission_factor_table.sql, server/src/main/java/com/example/carboncalculator/entities/EmissionFactor.java
- Notas: Tabela global SEM RLS. UNIQUE(year, month). CHECK value > 0. CHECK month 1..12.

## T-042 — EmissionFactorRepository + Service + Controller (CRUD) [concluida]
- Refs: US-026, AC-072, AC-073, AC-074, AC-075, AC-076, AC-077, AC-078
- Arquivos: server/src/main/java/com/example/carboncalculator/repositories/EmissionFactorRepository.java, server/src/main/java/com/example/carboncalculator/services/EmissionFactorService.java, server/src/main/java/com/example/carboncalculator/controllers/EmissionFactorController.java, server/src/main/java/com/example/carboncalculator/dto/EmissionFactorDTO.java, server/src/main/java/com/example/carboncalculator/dto/CreateEmissionFactorRequest.java
- Esforço: medio
- Notas: Depende de T-041. Endpoint global (sem X-Institution-Id para leitura). Admin para CUD. Filtro por ano na listagem.

## T-043 — EmissionCalculationService (motor de cálculo) [concluida]
- Refs: US-028, AC-083, AC-084, AC-085, AC-086, AC-087, AC-088, AC-089, AC-090, AC-091, AC-092
- Arquivos: server/src/main/java/com/example/carboncalculator/services/EmissionCalculationService.java, server/src/main/java/com/example/carboncalculator/dto/EmissionResultDTO.java
- Esforço: alto
- Notas: Usa PeriodSummaryService para horas por lab/mês. Busca configurações via LaboratoryEquipment. Agrega em todas as dimensões. Resultado como DTO imutável (records).

## T-044 — EmissionController (cálculo, readiness, export) [concluida]
- Refs: US-027, US-028, US-030, AC-079, AC-080, AC-081, AC-082, AC-098, AC-099, AC-100, AC-101
- Arquivos: server/src/main/java/com/example/carboncalculator/controllers/EmissionController.java, server/src/main/java/com/example/carboncalculator/dto/ReadinessDTO.java
- Esforço: medio
- Notas: Depende de T-043. Rotas sob /api/v1/academic-periods/{periodId}/emissions. Export CSV como StreamingResponseBody.

## T-045 — Testes de integração do backend (fatores + cálculo) [concluida]
- Refs: AC-072, AC-073, AC-074, AC-075, AC-076, AC-077, AC-078, AC-079, AC-080, AC-081, AC-082, AC-083, AC-084, AC-085, AC-086, AC-087, AC-088, AC-089, AC-090, AC-091, AC-092, AC-098, AC-099, AC-100, AC-101
- Arquivos: server/src/test/java/com/example/carboncalculator/EmissionFactorIntegrationTest.java, server/src/test/java/com/example/carboncalculator/EmissionCalculationIntegrationTest.java
- Esforço: alto
- Notas: Depende de T-042, T-043, T-044. Testes com PostgreSQL real. Seed de dados no @BeforeEach. Cada cenário do TDD vira um caso de teste com @spec:AC-xxx no nome.

## T-046 — API client frontend (emission-factors + emissions) [concluida]
- Refs: US-026, US-027, US-028, US-030
- Arquivos: client/src/lib/api/emission-factors.ts, client/src/lib/api/emissions.ts
- Notas: Segue padrão de client/src/lib/api/equipment-models.ts. Tipagem TypeScript dos DTOs de resposta.

## T-047 — Sidebar + rotas (emissões e fatores) [concluida]
- Refs: US-029
- Arquivos: client/src/components/layout/AppLayout.tsx, client/src/App.tsx
- Notas: "Emissões" na seção Análise (ícone Leaf), visível para todos. "Fatores de Emissão" na seção Configuração (ícone Percent), admin only.

## T-048 — EmissionFactorsPage (CRUD frontend) [concluida]
- Refs: US-026, AC-072, AC-075, AC-076, AC-077
- Arquivos: client/src/pages/emission-factors/EmissionFactorsPage.tsx, client/src/lib/schemas/emissionFactorSchema.ts
- Esforço: medio
- Notas: Tabela com filtro por ano. Formulário inline ou dialog. Zod + React Hook Form.

## T-049 — EmissionsDashboard (página principal de emissões) [concluida]
- Refs: US-029, AC-093, AC-094, AC-095, AC-096, AC-097
- Arquivos: client/src/pages/emissions/EmissionsDashboardPage.tsx, client/src/components/emissions/EquivalenceCards.tsx, client/src/components/emissions/ReadinessCheck.tsx, client/src/components/emissions/ExportButton.tsx
- Esforço: alto
- Notas: Depende de T-046 e T-047. Instalar recharts com bun. Select de período. Cards de totais. Gráficos Recharts. Painel de transparência colapsável.

## T-050 — Seed de fatores de emissão [concluida]
- Refs: US-026, AC-075
- Arquivos: server/src/main/resources/db/migration/afterMigrate.sql
- Notas: Fatores do SIN 2025 (jan–jul) com valores reais do MCTI. INSERT ON CONFLICT DO NOTHING.
