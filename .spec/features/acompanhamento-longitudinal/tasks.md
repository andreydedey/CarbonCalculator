# Tasks: acompanhamento-longitudinal

> feature: acompanhamento-longitudinal

## T-051 — Migration V19: tabela emission_snapshot [concluida]
- Refs: US-033, US-034, AC-103, AC-112
- Arquivos: server/src/main/resources/db/migration/V19__create_emission_snapshot_table.sql
- Esforço: baixo

## T-052 — Entidade, repositório e DTO de EmissionSnapshot [concluida]
- Refs: US-033, US-034, AC-103, AC-104, AC-105, AC-106, AC-107, AC-108, AC-109, AC-110, AC-111, AC-112
- Arquivos: server/src/main/java/com/example/carboncalculator/entities/EmissionSnapshot.java, server/src/main/java/com/example/carboncalculator/repositories/EmissionSnapshotRepository.java, server/src/main/java/com/example/carboncalculator/dto/SnapshotAggregateDTO.java
- Esforço: baixo

## T-053 — EmissionSnapshotCronService: captura diária automática [concluida]
- Refs: US-034, AC-112, AC-113, AC-114, AC-115, AC-116, AC-117
- Arquivos: server/src/main/java/com/example/carboncalculator/services/EmissionSnapshotCronService.java, server/src/main/java/com/example/carboncalculator/CarboncalculatorApplication.java
- Esforço: alto

## T-054 — EmissionSnapshotQueryService: agregação por granularidade [concluida]
- Refs: US-033, AC-103, AC-104, AC-105, AC-106, AC-107, AC-108, AC-109, AC-110
- Arquivos: server/src/main/java/com/example/carboncalculator/services/EmissionSnapshotQueryService.java
- Esforço: alto

## T-055 — EmissionSnapshotController: GET /api/v1/snapshots [concluida]
- Refs: US-033, AC-103, AC-104, AC-105, AC-106, AC-107, AC-108, AC-109, AC-110, AC-111
- Arquivos: server/src/main/java/com/example/carboncalculator/controllers/EmissionSnapshotController.java
- Esforço: baixo

## T-056 — Testes de integração: cron e query [concluida]
- Refs: US-033, US-034, AC-103, AC-104, AC-105, AC-106, AC-107, AC-108, AC-109, AC-110, AC-111, AC-112, AC-113, AC-114, AC-115, AC-116, AC-117
- Arquivos: server/src/test/java/com/example/carboncalculator/EmissionSnapshotIntegrationTest.java
- Esforço: alto

## T-057 — Testes unitários: EmissionSnapshotQueryService [concluida]
- Refs: US-033, AC-103, AC-107, AC-108
- Arquivos: server/src/test/java/com/example/carboncalculator/EmissionSnapshotQueryServiceTest.java
- Esforço: médio

## T-058 — LongitudinalPage: React com toggle, gráfico e tabela [concluida]
- Refs: US-035, AC-118, AC-119, AC-120, AC-121
- Arquivos: client/src/pages/longitudinal/LongitudinalPage.tsx, client/src/pages/longitudinal/LongitudinalPage.logic.ts, client/src/lib/api/snapshots.ts, client/src/lib/schemas/snapshotSchema.ts
- Esforço: alto

## T-059 — Testes de componente: LongitudinalPage [concluida]
- Refs: US-035, AC-118, AC-119, AC-120, AC-121
- Arquivos: client/src/pages/longitudinal/LongitudinalPage.test.ts
- Esforço: médio
