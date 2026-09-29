# Tasks: Calendário letivo

> feature: calendario-letivo

## T-026 — Migrations: turnos e novo modelo de schedule [concluida]
- Refs: US-024, US-020, AC-067, AC-059
- Arquivos: server/src/main/resources/db/migration/V15__create_academic_period_tables.sql
- Esforço: medio
- Notas: Reescrever migration para incluir academic_period_shift (com shift_type, start_time, classes_per_day, class_duration_minutes, break_duration_minutes, active_days SMALLINT[], enabled). Redesenhar laboratory_schedule com FK para shift_id, day_of_week e occupied_slots SMALLINT[]. Adicionar coluna type VARCHAR(20) em academic_period_holiday. Manter btree_gist e EXCLUDE para sobreposição de períodos.

## T-027 — Entidades JPA atualizadas [concluida]
- Refs: US-024, US-020, US-019, AC-067, AC-059, AC-057
- Arquivos: server/src/main/java/com/example/carboncalculator/entities/AcademicPeriod.java, server/src/main/java/com/example/carboncalculator/entities/AcademicPeriodHoliday.java, server/src/main/java/com/example/carboncalculator/entities/AcademicPeriodShift.java, server/src/main/java/com/example/carboncalculator/entities/LaboratorySchedule.java
- Esforço: medio
- Notas: Nova entidade AcademicPeriodShift. Atualizar AcademicPeriodHoliday com campo type. Redesenhar LaboratorySchedule com shiftId e occupiedSlots (array). Enum ShiftType (MORNING, AFTERNOON, EVENING) e HolidayType (NATIONAL, STATE, MUNICIPAL, RECESS).

## T-028 — DTOs e schemas atualizados [concluida]
- Refs: US-024, US-020, US-019, AC-067, AC-059, AC-057
- Arquivos: server/src/main/java/com/example/carboncalculator/dto/ShiftDTO.java, server/src/main/java/com/example/carboncalculator/dto/ReplaceShiftsRequest.java, server/src/main/java/com/example/carboncalculator/dto/AcademicPeriodDTO.java, server/src/main/java/com/example/carboncalculator/dto/HolidayDTO.java, server/src/main/java/com/example/carboncalculator/dto/ReplaceHolidaysRequest.java, server/src/main/java/com/example/carboncalculator/dto/ScheduleEntryDTO.java, server/src/main/java/com/example/carboncalculator/dto/ReplaceScheduleRequest.java, server/src/main/java/com/example/carboncalculator/dto/PeriodSummaryDTO.java
- Esforço: medio
- Notas: Novos DTOs para turnos, atualizar holiday com type, redesenhar schedule com entries e occupiedSlots array.

## T-029 — ShiftService com validações [concluida]
- Refs: US-024, AC-067, AC-068, AC-069
- Arquivos: server/src/main/java/com/example/carboncalculator/services/ShiftService.java, server/src/main/java/com/example/carboncalculator/repositories/AcademicPeriodShiftRepository.java
- Esforço: medio
- Notas: PUT batch de turnos. Validar classesPerDay > 0, classDurationMinutes > 0, breakDurationMinutes >= 0, duração cabe no intervalo, no máximo 1 por shiftType. Calcular endTime.

## T-030 — AcademicPeriodService atualizado [concluida]
- Refs: US-018, US-019, US-022, AC-051, AC-052, AC-053, AC-055, AC-056, AC-057, AC-058, AC-065
- Arquivos: server/src/main/java/com/example/carboncalculator/services/AcademicPeriodService.java
- Esforço: alto
- Notas: CRUD de períodos com sobreposição, feriados com type, cascade inclui turnos, cópia inclui turnos e grades. Atualizar para o novo modelo.

## T-031 — LaboratoryScheduleService com validação de slots [concluida]
- Refs: US-020, AC-059, AC-060, AC-061
- Arquivos: server/src/main/java/com/example/carboncalculator/services/LaboratoryScheduleService.java, server/src/main/java/com/example/carboncalculator/repositories/LaboratoryScheduleRepository.java
- Esforço: medio
- Notas: PUT batch por lab. Validar slots dentro de [1, classesPerDay], dayOfWeek dentro de activeDays do turno. Depende de T-029.

## T-032 — PeriodSummaryService com cálculo por slots [concluida]
- Refs: US-021, AC-062, AC-063, AC-064, AC-070
- Arquivos: server/src/main/java/com/example/carboncalculator/services/PeriodSummaryService.java
- Esforço: alto
- Notas: Dias letivos por mês considerando activeDays de cada turno. Horas = len(occupiedSlots) × classDurationMinutes × diasLetivos ÷ 60. Turnos disabled não contabilizam.

## T-033 — Controllers REST atualizados [concluida]
- Refs: US-018, US-019, US-020, US-021, US-022, US-023, US-024, AC-051, AC-054, AC-057, AC-059, AC-062, AC-065, AC-066, AC-067
- Arquivos: server/src/main/java/com/example/carboncalculator/controllers/AcademicPeriodController.java, server/src/main/java/com/example/carboncalculator/controllers/LaboratoryScheduleController.java
- Esforço: medio
- Notas: Novo endpoint PUT /shifts. Atualizar contratos de holidays (com type) e schedule (com entries e slots). Depende de T-029, T-030, T-031, T-032.

## T-034 — Seed data com turnos e slots [concluida]
- Refs: US-018, US-019, US-020, US-024
- Arquivos: server/src/main/resources/db/migration/afterMigrate.sql
- Esforço: medio
- Notas: Seed com período 2025.1, turnos (Manhã 5×50+10, Tarde 5×50+10, Noite 4×50+10), feriados com tipo, grades com slots variados por lab.

## T-035 — Frontend: API client e tipos atualizados [concluida]
- Refs: US-018, US-019, US-020, US-021, US-022, US-024
- Arquivos: client/src/lib/api/academic-periods.ts, client/src/lib/schemas/academicPeriodSchema.ts
- Esforço: baixo
- Notas: Tipos para shifts, holiday types, schedule entries com occupiedSlots. Funções API para PUT shifts, PUT holidays, PUT schedule, GET summary.

## T-036 — Frontend: página Calendário Letivo (cards + turnos + resumo) [concluida]
- Refs: US-018, US-024, AC-051, AC-054, AC-067
- Arquivos: client/src/pages/academic-periods/AcademicPeriodsPage.tsx, client/src/components/academic-periods/AcademicPeriodCard.tsx, client/src/components/academic-periods/ShiftSummaryTable.tsx, client/src/components/academic-periods/PeriodSummary.tsx
- Esforço: alto
- Notas: Cards de semestre lado a lado (design frame 5), tabela de turnos com coluna Intervalo, grid resumo de ocupação com mini-strips M/T/N por dia.

## T-037 — Frontend: modal Configurar Turnos [concluida]
- Refs: US-024, AC-067, AC-068, AC-069
- Arquivos: client/src/components/academic-periods/ShiftConfigModal.tsx
- Esforço: alto
- Notas: Modal com 3 seções (Manhã/Tarde/Noite), switch on/off, inputs de horário início, aulas/dia, duração, intervalo, select de dias ativos, preview visual de slots com horários calculados (design frame 5b).

## T-038 — Frontend: página Ocupação dos Laboratórios [concluida]
- Refs: US-020, AC-059, AC-060, AC-061
- Arquivos: client/src/pages/academic-periods/OccupationEditorPage.tsx, client/src/components/academic-periods/OccupationGrid.tsx
- Esforço: xalto
- Notas: Página dedicada (design frame 5c). Tabs por lab, stats cards (aulas/sem, horas/sem, taxa de ocupação), grade semanal com linha por slot de aula, checkboxes individuais por dia, linhas de intervalo, totais por dia, botão salvar.

## T-039 — Frontend: editor de feriados com calendário shadcn [concluida]
- Refs: US-019, AC-057, AC-058
- Arquivos: client/src/components/academic-periods/HolidayEditor.tsx
- Esforço: medio
- Notas: Tabela de feriados com colunas Data, Feriado/Recesso, Tipo (badge). Date picker com calendário shadcn. Select de tipo de feriado.

## T-040 — Frontend: sidebar, rotas e formulários [concluida]
- Refs: US-018, US-022, AC-055, AC-065
- Arquivos: client/src/components/layout/AppLayout.tsx, client/src/App.tsx, client/src/components/academic-periods/AcademicPeriodForm.tsx, client/src/components/academic-periods/CopyPeriodDialog.tsx
- Esforço: medio
- Notas: Sidebar item "Calendário Letivo", rotas /academic-periods, /academic-periods/:id, /academic-periods/:id/occupation. Dialog de criação/edição e cópia de período.
