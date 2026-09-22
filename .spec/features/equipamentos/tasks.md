# Tasks: Equipamentos

> feature: equipamentos

## T-021 — Migrations Flyway (equipment_model + RLS + laboratory_equipment) [pendente]
- Refs: US-012, US-015, AC-034, AC-039, AC-043, AC-045
- Arquivos: server/src/main/resources/db/migration/V10__create_equipment_model_table.sql, server/src/main/resources/db/migration/V11__create_laboratory_equipment_table.sql
- Notas: V10 cria tabela equipment_model com RLS policy (mesmo padrão de laboratory). V11 cria laboratory_equipment com FK, UNIQUE(laboratory_id, equipment_model_id, operating_system), CHECK(quantity > 0). Índices em institution_id, laboratory_id, equipment_model_id.

## T-022 — Entidades JPA (EquipmentModel + LaboratoryEquipment) [pendente]
- Refs: US-012, US-015
- Arquivos: server/src/main/java/com/example/carboncalculator/entities/EquipmentModel.java, server/src/main/java/com/example/carboncalculator/entities/LaboratoryEquipment.java
- Notas: Lombok @Builder/@Getter/@Setter, UUID gerado, timestamps via @PrePersist/@PreUpdate. Mesmo padrão de Laboratory.java. Depende de T-021.

## T-023 — Repositories + DTOs + Mappers [pendente]
- Refs: US-012, US-013, US-015, US-016
- Arquivos: server/src/main/java/com/example/carboncalculator/repositories/EquipmentModelRepository.java, server/src/main/java/com/example/carboncalculator/repositories/LaboratoryEquipmentRepository.java, server/src/main/java/com/example/carboncalculator/dto/CreateEquipmentModelRequest.java, server/src/main/java/com/example/carboncalculator/dto/EquipmentModelDTO.java, server/src/main/java/com/example/carboncalculator/dto/CreateLaboratoryEquipmentRequest.java, server/src/main/java/com/example/carboncalculator/dto/LaboratoryEquipmentDTO.java, server/src/main/java/com/example/carboncalculator/dto/LaboratoryCompositionDTO.java, server/src/main/java/com/example/carboncalculator/mappers/EquipmentModelMapper.java, server/src/main/java/com/example/carboncalculator/mappers/LaboratoryEquipmentMapper.java
- Notas: Records Java para DTOs. EquipmentModelDTO inclui campo derivado hasMonitor. LaboratoryCompositionDTO agrupa items + totalMachines + modelsWithoutMonitor. Depende de T-022.

## T-024 — EquipmentModelService + Controller [pendente]
- Refs: US-012, US-013, US-014, AC-034, AC-035, AC-036, AC-037, AC-038, AC-039, AC-040, AC-041, AC-042
- Arquivos: server/src/main/java/com/example/carboncalculator/services/EquipmentModelService.java, server/src/main/java/com/example/carboncalculator/controllers/EquipmentModelController.java
- Esforço: alto
- Notas: CRUD + busca por nome (LIKE case-insensitive via Spring Data query method). Listagem paginada com sort por nome. Bloqueio de exclusão se modelo vinculado (409). Validação de nome obrigatório na service layer. Depende de T-023.

## T-025 — LaboratoryEquipmentService + Controller [pendente]
- Refs: US-015, US-016, US-017, AC-043, AC-044, AC-045, AC-046, AC-047, AC-048, AC-049, AC-050
- Arquivos: server/src/main/java/com/example/carboncalculator/services/LaboratoryEquipmentService.java, server/src/main/java/com/example/carboncalculator/controllers/LaboratoryEquipmentController.java
- Esforço: alto
- Notas: Vincular/desvincular modelo de lab. Validação de quantidade > 0 na service. Duplicidade (409) via existsByLabIdAndModelIdAndOS. Composição com totalMachines e modelsWithoutMonitor. Depende de T-023, T-024.

## T-026 — Frontend: API clients + schemas Zod [pendente]
- Refs: US-012, US-015
- Arquivos: client/src/lib/api/equipment-models.ts, client/src/lib/api/laboratory-equipment.ts, client/src/lib/schemas/equipmentModelSchema.ts, client/src/lib/schemas/laboratoryEquipmentSchema.ts
- Notas: Funções de fetch seguindo padrão de laboratories.ts. Schemas Zod: name obrigatório, memoryGb positivo se informado, quantity inteiro > 0. Depende de T-024, T-025.

## T-027 — Frontend: Página de modelos de equipamento [pendente]
- Refs: US-012, US-013, US-014, AC-034, AC-035, AC-036, AC-037, AC-038, AC-040, AC-041, AC-042
- Arquivos: client/src/pages/equipment-models/EquipmentModelsPage.tsx, client/src/components/equipment-models/EquipmentModelForm.tsx, client/src/components/equipment-models/EquipmentModelCard.tsx, client/src/App.tsx
- Esforço: alto
- Notas: Listagem paginada com busca por nome. Dialog CRUD (reutilizar padrões de LaboratoryList). Cards mostram specs do hardware + badge "Sem monitor" quando aplicável. Rota /equipment-models no router. Depende de T-026.

## T-028 — Frontend: Composição do laboratório (vinculação + tabela) [pendente]
- Refs: US-015, US-016, US-017, AC-043, AC-044, AC-045, AC-046, AC-047, AC-048, AC-049, AC-050
- Arquivos: client/src/components/laboratories/LaboratoryEquipmentSection.tsx, client/src/components/laboratories/LinkEquipmentDialog.tsx, client/src/components/laboratories/LaboratoryForm.tsx
- Esforço: alto
- Notas: Substituir placeholder "Equipamentos do Laboratório" por tabela funcional com modelo, OS, quantidade, monitor badge, ações (editar/desvincular). Dialog de vinculação com select de modelos + OS + quantidade. Rodapé com total de máquinas + alerta de monitor. Depende de T-026, T-027.

## T-029 — Testes de integração (backend + RLS) [pendente]
- Refs: AC-034, AC-035, AC-036, AC-037, AC-038, AC-039, AC-040, AC-041, AC-042, AC-043, AC-044, AC-045, AC-046, AC-047, AC-048, AC-049, AC-050
- Arquivos: server/src/test/java/com/example/carboncalculator/controllers/EquipmentModelControllerIntegrationTest.java, server/src/test/java/com/example/carboncalculator/controllers/LaboratoryEquipmentControllerIntegrationTest.java
- Esforço: alto
- Notas: Testcontainers + PostgreSQL real (RLS). Cada AC vira um @Test. Testa isolamento entre instituições, bloqueio de exclusão, duplicidade de vínculo, composição com totais. Depende de T-021 a T-025.

## T-030 — Testes unitários (services) [pendente]
- Refs: AC-035, AC-041, AC-042, AC-044, AC-045, AC-049
- Arquivos: server/src/test/java/com/example/carboncalculator/services/EquipmentModelServiceTest.java, server/src/test/java/com/example/carboncalculator/services/LaboratoryEquipmentServiceTest.java
- Notas: Mock dos repositories. Testa validações: nome obrigatório, bloqueio de exclusão com vínculos, quantidade <= 0, duplicidade. Depende de T-024, T-025.
