# Tasks: acompanhamento-longitudinal

> feature: acompanhamento-longitudinal

## T-051 — Migration V23: tabela emission_snapshot [concluida]
- Refs: US-044, AC-136
- Arquivos: server/src/main/resources/db/migration/V23__create_emission_snapshot_table.sql
- Esforço: baixo

## T-052 — Entidade, repositório e DTO de EmissionSnapshot [concluida]
- Refs: US-044, AC-136, AC-137, AC-138, AC-139, AC-140, AC-141, AC-142, AC-143, AC-144
- Arquivos: server/src/main/java/com/example/carboncalculator/entities/EmissionSnapshot.java, server/src/main/java/com/example/carboncalculator/repositories/EmissionSnapshotRepository.java, server/src/main/java/com/example/carboncalculator/dto/SnapshotAggregateDTO.java
- Esforço: baixo

## T-054 — EmissionSnapshotQueryService: agregação por granularidade [concluida]
- Refs: US-044, AC-136, AC-137, AC-138, AC-139, AC-140, AC-141, AC-142, AC-143
- Arquivos: server/src/main/java/com/example/carboncalculator/services/EmissionSnapshotQueryService.java
- Esforço: alto

## T-055 — EmissionSnapshotController: GET /api/v1/snapshots [concluida]
- Refs: US-044, AC-136, AC-137, AC-138, AC-139, AC-140, AC-141, AC-142, AC-143, AC-144
- Arquivos: server/src/main/java/com/example/carboncalculator/controllers/EmissionSnapshotController.java
- Esforço: baixo

## T-056 — Testes de integração: query [concluida]
- Refs: US-044, AC-136, AC-137, AC-138, AC-139, AC-140, AC-141, AC-142, AC-143, AC-144
- Arquivos: server/src/test/java/com/example/carboncalculator/EmissionSnapshotIntegrationTest.java
- Esforço: alto

## T-057 — Testes unitários: EmissionSnapshotQueryService [concluida]
- Refs: US-044, AC-136, AC-140, AC-141
- Arquivos: server/src/test/java/com/example/carboncalculator/EmissionSnapshotQueryServiceTest.java
- Esforço: médio

## T-058 — LongitudinalPage: React com toggle, gráfico e tabela [concluida]
- Refs: US-046, AC-151, AC-152, AC-153, AC-154
- Arquivos: client/src/pages/longitudinal/LongitudinalPage.tsx, client/src/pages/longitudinal/LongitudinalPage.logic.ts, client/src/lib/api/snapshots.ts
- Esforço: alto

## T-059 — Testes de componente: LongitudinalPage [concluida]
- Refs: US-046, AC-151, AC-152, AC-153, AC-154
- Arquivos: client/src/pages/longitudinal/LongitudinalPage.test.ts
- Esforço: médio
