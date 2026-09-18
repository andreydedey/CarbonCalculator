# Tasks: Instituições e Laboratórios

> feature: instituicoes-e-laboratorios

## T-001 — Migrations Flyway (tabelas + RLS) [pendente]

- Refs: US-001, US-002, AC-008
- Arquivos: server/src/main/resources/db/migration/V1__create_institution_table.sql, server/src/main/resources/db/migration/V2__create_laboratory_table.sql, server/src/main/resources/db/migration/V3__enable_rls_laboratory.sql
- Notas: Cria as tabelas institution e laboratory, índices, e habilita RLS em laboratory com policy baseada em current_setting('app.current_institution')

## T-002 — Entidades JPA [pendente]

- Refs: US-001, US-002
- Arquivos: server/src/main/java/com/example/carboncalculator/entities/Institution.java, server/src/main/java/com/example/carboncalculator/entities/Laboratory.java
- Notas: Entities com Lombok, UUID gerado automaticamente, timestamps via @PrePersist/@PreUpdate. Depende de T-001.

## T-003 — Tenant filter (servlet filter para RLS) [pendente]

- Refs: AC-008, AC-009, AC-010
- Arquivos: server/src/main/java/com/example/carboncalculator/config/TenantFilter.java, server/src/main/java/com/example/carboncalculator/config/TenantContext.java
- Notas: Lê header X-Institution-Id, executa SET LOCAL app.current_institution na conexão JDBC. Retorna 400 se header ausente em rotas de tenant. Endpoints de institution são excluídos do filtro.

## T-004 — Repositories [pendente]

- Refs: US-001, US-002, US-003, US-005
- Arquivos: server/src/main/java/com/example/carboncalculator/repositories/InstitutionRepository.java, server/src/main/java/com/example/carboncalculator/repositories/LaboratoryRepository.java
- Notas: Spring Data JPA. LaboratoryRepository não precisa de filtro manual por institution — o RLS faz isso. Depende de T-002.

## T-005 — DTOs e Mappers [pendente]

- Refs: US-001, US-002
- Arquivos: server/src/main/java/com/example/carboncalculator/dto/CreateInstitutionRequest.java, server/src/main/java/com/example/carboncalculator/dto/InstitutionResponse.java, server/src/main/java/com/example/carboncalculator/dto/CreateLaboratoryRequest.java, server/src/main/java/com/example/carboncalculator/dto/LaboratoryResponse.java, server/src/main/java/com/example/carboncalculator/dto/UpdateInstitutionRequest.java, server/src/main/java/com/example/carboncalculator/dto/UpdateLaboratoryRequest.java, server/src/main/java/com/example/carboncalculator/mappers/InstitutionMapper.java, server/src/main/java/com/example/carboncalculator/mappers/LaboratoryMapper.java
- Notas: Records Java para DTOs. Mapper manual (sem MapStruct). Depende de T-002.

## T-006 — Services [pendente]

- Refs: US-001, US-002, US-003, US-005, AC-001, AC-002, AC-003, AC-004, AC-005, AC-006, AC-007, AC-011, AC-012, AC-013
- Arquivos: server/src/main/java/com/example/carboncalculator/services/InstitutionService.java, server/src/main/java/com/example/carboncalculator/services/LaboratoryService.java
- Esforço: alto
- Notas: Validação de UF, unicidade de sigla, criação atômica institution+lab, soft-delete, bloqueio de exclusão com dependentes. Depende de T-004, T-005.

## T-007 — Controllers REST [pendente]

- Refs: US-001, US-002, US-003, US-005, AC-001, AC-004, AC-009
- Arquivos: server/src/main/java/com/example/carboncalculator/controllers/InstitutionController.java, server/src/main/java/com/example/carboncalculator/controllers/LaboratoryController.java
- Notas: Endpoints conforme TDD. Institution não precisa de header tenant; laboratory sim. Depende de T-003, T-006.

## T-008 — Setup frontend (Biome, Zod, RHF, Router, HTTP client) [pendente]

- Refs: US-001, US-004
- Arquivos: client/package.json, client/biome.json, client/src/lib/api/client.ts, client/src/lib/api/institutions.ts, client/src/lib/api/laboratories.ts, client/src/main.tsx
- Notas: Instalar dependências, configurar Biome substituindo oxlint, criar HTTP client com interceptor que injeta X-Institution-Id, configurar React Router.

## T-009 — Contexto de instituição e layout [pendente]

- Refs: US-004, AC-008, AC-009
- Arquivos: client/src/context/InstitutionContext.tsx, client/src/layout/AppLayout.tsx, client/src/layout/InstitutionSwitcher.tsx
- Notas: InstitutionContext persiste institutionId em localStorage. AppLayout com sidebar conforme design. InstitutionSwitcher no header. Depende de T-008.

## T-010 — Formulário de instituição [pendente]

- Refs: US-001, AC-001, AC-002, AC-003
- Arquivos: client/src/pages/institutions/InstitutionForm.tsx, client/src/lib/schemas/institutionSchema.ts
- Notas: Dois cards conforme design (dados da instituição + laboratório vinculado). Schema Zod valida UF contra lista de 27 estados. Depende de T-008, T-009.

## T-011 — Lista de laboratórios [pendente]

- Refs: US-003, AC-006, AC-007
- Arquivos: client/src/pages/laboratories/LaboratoryList.tsx
- Notas: Cards conforme design com nome, status e badge. Consultar o design (ADR-003). Depende de T-008, T-009.

## T-012 — Formulário de laboratório e desativação [pendente]

- Refs: US-002, US-005, AC-004, AC-005, AC-011
- Arquivos: client/src/pages/laboratories/LaboratoryForm.tsx, client/src/pages/laboratories/DeactivateDialog.tsx, client/src/lib/schemas/laboratorySchema.ts
- Notas: Formulário de criação/edição + diálogo de confirmação de desativação. Depende de T-008, T-009.

## T-013 — Testes de integração (backend + RLS) [pendente]

- Refs: AC-001, AC-002, AC-003, AC-004, AC-005, AC-006, AC-007, AC-008, AC-009, AC-010, AC-011, AC-012, AC-013
- Arquivos: server/src/test/java/com/example/carboncalculator/controllers/InstitutionControllerIntegrationTest.java, server/src/test/java/com/example/carboncalculator/controllers/LaboratoryControllerIntegrationTest.java
- Esforço: alto
- Notas: Testcontainers com PostgreSQL real (RLS não funciona em H2). Cada AC vira um @Test anotado com @spec:AC-xxx. Depende de T-001 a T-007.

## T-014 — Testes unitários (services) [pendente]

- Refs: AC-002, AC-003, AC-005, AC-012, AC-013
- Arquivos: server/src/test/java/com/example/carboncalculator/services/InstitutionServiceTest.java, server/src/test/java/com/example/carboncalculator/services/LaboratoryServiceTest.java
- Notas: Mock dos repositories. Testa validações de negócio isoladamente. Depende de T-006.
