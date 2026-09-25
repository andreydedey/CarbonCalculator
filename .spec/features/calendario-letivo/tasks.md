# Tasks: Calendário letivo

> feature: calendario-letivo

## T-014 — Migrations e entidades JPA [concluida]
- Refs: US-018, US-019, US-020, AC-051, AC-066
- Arquivos: server/src/main/resources/db/migration/V15__create_academic_period_tables.sql, server/src/main/java/com/example/carboncalculator/entities/AcademicPeriod.java, server/src/main/java/com/example/carboncalculator/entities/AcademicPeriodHoliday.java, server/src/main/java/com/example/carboncalculator/entities/LaboratorySchedule.java
- Esforço: medio
- Notas: Criar tabelas academic_period (com RLS e EXCLUDE para sobreposição), academic_period_holiday, laboratory_schedule. Ativar extensão btree_gist. Entidades JPA com @PrePersist/@PreUpdate.

## T-015 — Repositories e DTOs [concluida]
- Refs: US-018, US-019, US-020, AC-054
- Arquivos: server/src/main/java/com/example/carboncalculator/repositories/AcademicPeriodRepository.java, server/src/main/java/com/example/carboncalculator/repositories/AcademicPeriodHolidayRepository.java, server/src/main/java/com/example/carboncalculator/repositories/LaboratoryScheduleRepository.java, server/src/main/java/com/example/carboncalculator/dto/AcademicPeriodDTO.java, server/src/main/java/com/example/carboncalculator/dto/HolidayDTO.java, server/src/main/java/com/example/carboncalculator/dto/ScheduleBlockDTO.java, server/src/main/java/com/example/carboncalculator/dto/CreateAcademicPeriodRequest.java, server/src/main/java/com/example/carboncalculator/dto/UpdateAcademicPeriodRequest.java, server/src/main/java/com/example/carboncalculator/dto/ReplaceHolidaysRequest.java, server/src/main/java/com/example/carboncalculator/dto/ReplaceScheduleRequest.java, server/src/main/java/com/example/carboncalculator/dto/CopyPeriodRequest.java, server/src/main/java/com/example/carboncalculator/dto/PeriodSummaryDTO.java
- Esforço: medio
- Notas: Records de request/response, repositórios JPA.

## T-016 — AcademicPeriodService com validações [concluida]
- Refs: US-018, US-019, US-022, AC-051, AC-052, AC-053, AC-055, AC-056, AC-057, AC-058, AC-065
- Arquivos: server/src/main/java/com/example/carboncalculator/services/AcademicPeriodService.java, server/src/main/java/com/example/carboncalculator/exceptions/PeriodOverlapException.java, server/src/main/java/com/example/carboncalculator/exceptions/PeriodNotFoundException.java, server/src/main/java/com/example/carboncalculator/exceptions/HolidayOutOfRangeException.java
- Esforço: alto
- Notas: CRUD de períodos com validação de sobreposição, feriados batch replace, cópia de período anterior. Depende de T-014 e T-015.

## T-017 — LaboratoryScheduleService com validações [concluida]
- Refs: US-020, AC-059, AC-060, AC-061
- Arquivos: server/src/main/java/com/example/carboncalculator/services/LaboratoryScheduleService.java, server/src/main/java/com/example/carboncalculator/exceptions/ScheduleBlockOverlapException.java
- Esforço: medio
- Notas: Grade de ocupação batch replace, validação de blocos sobrepostos e horários inválidos. Depende de T-014 e T-015.

## T-018 — PeriodSummaryService (cálculos derivados) [concluida]
- Refs: US-021, AC-062, AC-063, AC-064
- Arquivos: server/src/main/java/com/example/carboncalculator/services/PeriodSummaryService.java
- Esforço: alto
- Notas: Cálculo de dias letivos por mês (descontando fins de semana e feriados) e horas de uso por laboratório por mês. Lógica pura em memória. Depende de T-014 e T-015.

## T-019 — Controllers REST [concluida]
- Refs: US-018, US-019, US-020, US-021, US-022, AC-051, AC-054, AC-057, AC-059, AC-062, AC-065
- Arquivos: server/src/main/java/com/example/carboncalculator/controllers/AcademicPeriodController.java, server/src/main/java/com/example/carboncalculator/controllers/LaboratoryScheduleController.java
- Esforço: medio
- Notas: Endpoints REST conforme TDD. @PreAuthorize para MANAGER (escrita) e RESEARCHER (leitura). Depende de T-016, T-017 e T-018.

## T-020 — GlobalExceptionHandler — novas exceções [concluida]
- Refs: AC-052, AC-053, AC-058, AC-060, AC-061
- Arquivos: server/src/main/java/com/example/carboncalculator/exceptions/GlobalExceptionHandler.java
- Esforço: baixo
- Notas: Mapear novas exceções para HTTP 400/409. Depende de T-016 e T-017.

## T-021 — Seed data para calendário [concluida]
- Refs: US-018, US-019, US-020
- Arquivos: server/src/main/resources/db/migration/afterMigrate.sql
- Esforço: baixo
- Notas: Adicionar período 2024.1 com feriados e grades para UFPA e UNICAMP no seed.

## T-022 — Frontend: API client e tipos [concluida]
- Refs: US-018, US-019, US-020, US-021, US-022
- Arquivos: client/src/lib/api/academic-periods.ts, client/src/lib/schemas/academicPeriodSchema.ts
- Esforço: baixo
- Notas: Tipos TypeScript, funções API com axios, schemas Zod.

## T-023 — Frontend: listagem e CRUD de períodos [concluida]
- Refs: US-018, AC-051, AC-054, AC-055, AC-056
- Arquivos: client/src/pages/academic-periods/AcademicPeriodsPage.tsx, client/src/components/academic-periods/AcademicPeriodCard.tsx, client/src/components/academic-periods/AcademicPeriodForm.tsx
- Esforço: medio
- Notas: Listagem paginada com cards, dialog de criação/edição, exclusão com confirmação.

## T-024 — Frontend: detalhe do período (feriados + grades + resumo) [concluida]
- Refs: US-019, US-020, US-021, AC-057, AC-059, AC-062, AC-063
- Arquivos: client/src/pages/academic-periods/AcademicPeriodDetailPage.tsx, client/src/components/academic-periods/HolidayEditor.tsx, client/src/components/academic-periods/ScheduleGrid.tsx, client/src/components/academic-periods/PeriodSummary.tsx
- Esforço: alto
- Notas: Página de detalhe com abas ou seções: feriados editáveis, grade por lab, resumo de dias/horas.

## T-025 — Frontend: sidebar, rotas e cópia de período [concluida]
- Refs: US-022, AC-065
- Arquivos: client/src/components/layout/AppLayout.tsx, client/src/App.tsx, client/src/components/academic-periods/CopyPeriodDialog.tsx
- Esforço: baixo
- Notas: Adicionar item "Calendário" na sidebar, rotas /academic-periods e /academic-periods/:id, dialog de cópia.
