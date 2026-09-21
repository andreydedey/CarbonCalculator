# Tasks: Acesso e papéis

> feature: acesso-e-papeis

## T-006 — Migrations e entidades JPA (app_user, user_institution, public_results) [concluida]
- Refs: US-006, US-007, US-009, US-010
- Arquivos: server/src/main/resources/db/migration/V4__create_app_user_table.sql, server/src/main/resources/db/migration/V5__create_user_institution_table.sql, server/src/main/resources/db/migration/V6__add_public_results_to_institution.sql, server/src/main/java/com/example/carboncalculator/models/AppUser.java, server/src/main/java/com/example/carboncalculator/models/UserInstitution.java, server/src/main/java/com/example/carboncalculator/models/InstitutionRole.java, server/src/main/java/com/example/carboncalculator/models/MembershipStatus.java, server/src/main/java/com/example/carboncalculator/repositories/AppUserRepository.java, server/src/main/java/com/example/carboncalculator/repositories/UserInstitutionRepository.java
- Esforço: medio
- Notas: Cria as tabelas app_user e user_institution com constraints, adiciona coluna public_results na institution. Entidades JPA + repositories + enums de role e status.

## T-007 — JWT service (geração, validação, refresh) [concluida]
- Refs: US-006, AC-014, AC-015, US-011, AC-032, AC-033
- Arquivos: server/pom.xml, server/src/main/java/com/example/carboncalculator/security/JwtService.java, server/src/main/java/com/example/carboncalculator/security/JwtAuthFilter.java, server/src/main/resources/application.yaml
- Esforço: alto
- Notas: Dependência jjwt no pom.xml. JwtService gera/valida access e refresh tokens. JwtAuthFilter extrai token do header Authorization e popula SecurityContext. Config JWT_SECRET via env var.

## T-008 — SecurityFilterChain e role hierarchy [concluida]
- Refs: US-009, AC-016, AC-025
- Arquivos: server/src/main/java/com/example/carboncalculator/config/SecurityConfig.java
- Esforço: medio
- Notas: Substitui o permitAll() atual. Configura rotas públicas (auth/login, auth/register), JWT filter, CORS, role hierarchy (ADMIN > GESTOR > PESQUISADOR). Depende de T-007.

## T-009 — AuthController (register, login, refresh, me) [concluida]
- Refs: US-006, US-007, US-011, AC-014, AC-015, AC-016, AC-017, AC-018, AC-019, AC-032, AC-033
- Arquivos: server/src/main/java/com/example/carboncalculator/controllers/AuthController.java, server/src/main/java/com/example/carboncalculator/services/AuthService.java, server/src/main/java/com/example/carboncalculator/dto/LoginRequest.java, server/src/main/java/com/example/carboncalculator/dto/RegisterRequest.java, server/src/main/java/com/example/carboncalculator/dto/AuthResponse.java, server/src/main/java/com/example/carboncalculator/dto/UserProfileDTO.java
- Esforço: alto
- Notas: Endpoints de autenticação. BCrypt para hash de senha. Login automático após registro. Ativação de convites pendentes no registro. Depende de T-006 e T-007.

## T-010 — Google OAuth2 [pendente]

- Refs: US-008, AC-020
- Arquivos: server/pom.xml, server/src/main/java/com/example/carboncalculator/security/OAuth2SuccessHandler.java, server/src/main/resources/application.yaml
- Esforço: alto
- Notas: spring-boot-starter-oauth2-client. Callback handler que cria/vincula AppUser pelo email Google e gera JWT. Redirect para frontend com token. Config GOOGLE_CLIENT_ID e GOOGLE_CLIENT_SECRET via env var.

## T-011 — TenantFilter: validar vínculo user↔institution [concluida]
- Refs: US-009, AC-021, AC-024
- Arquivos: server/src/main/java/com/example/carboncalculator/config/TenantFilter.java
- Esforço: medio
- Notas: Após autenticação, verificar que o usuário tem UserInstitution ACTIVE para o institution_id do header (ou é admin). Retorna 403 se não tiver. Depende de T-006 e T-008.

## T-012 — @PreAuthorize em controllers existentes [concluida]
- Refs: US-009, AC-021, AC-022, AC-023, AC-025
- Arquivos: server/src/main/java/com/example/carboncalculator/controllers/InstitutionController.java, server/src/main/java/com/example/carboncalculator/controllers/LaboratoryController.java
- Esforço: baixo
- Notas: Anotações @PreAuthorize em todos os endpoints. Instituições: GET isAuthenticated, POST hasRole ADMIN. Laboratórios: GET hasRole PESQUISADOR, POST/PATCH/DELETE hasRole GESTOR. InstitutionService.listForUser filtra por vínculos do usuário (admin vê tudo). Depende de T-008.

## T-013 — UserController e UserService (gestão de membros) [concluida]

- Refs: US-010, AC-026, AC-027, AC-028, AC-029, AC-030, AC-031
- Arquivos: server/src/main/java/com/example/carboncalculator/controllers/UserController.java, server/src/main/java/com/example/carboncalculator/services/UserService.java, server/src/main/java/com/example/carboncalculator/dto/InviteRequest.java, server/src/main/java/com/example/carboncalculator/dto/ChangeRoleRequest.java, server/src/main/java/com/example/carboncalculator/dto/UserMemberDTO.java, server/src/main/resources/db/migration/V7__add_user_email_to_user_institution.sql
- Esforço: alto
- Notas: GET /users (listar membros), POST /users/invite, PATCH /users/{id}/role, DELETE /users/{id}. Migration V7 permite user_id nullable para convites pendentes. Regras: não revogar/alterar a si mesmo, convite duplicado recusado, proteção contra remoção do último gestor. Depende de T-006, T-008 e T-011.

## T-014 — Frontend: AuthContext, interceptor e rotas protegidas [concluida]

- Refs: US-006, US-007, US-011, AC-014, AC-016, AC-032
- Arquivos: client/src/context/AuthContext.tsx, client/src/components/auth/ProtectedRoute.tsx, client/src/lib/api/auth.ts, client/src/App.tsx
- Esforço: alto
- Notas: AuthContext com JWT em memória, login(), register(), logout(), user. Axios interceptor adiciona Authorization header via api.defaults.headers. ProtectedRoute redireciona para /login. Refresh token via httpOnly cookie no mount da app.

## T-015 — Frontend: página de login [concluida]

- Refs: US-006, US-008, AC-014, AC-015, AC-020
- Arquivos: client/src/pages/auth/LoginPage.tsx, client/src/lib/schemas/authSchemas.ts
- Esforço: medio
- Notas: Tela conforme design 1a — painel esquerdo com brand EcoScope, painel direito com formulário (email, senha). Zod + RHF para validação. Link para registro e nota "Solicite ao gestor".

## T-016 — Frontend: página de registro [concluida]

- Refs: US-007, US-008, AC-017, AC-018, AC-020
- Arquivos: client/src/pages/auth/RegisterPage.tsx, client/src/lib/schemas/authSchemas.ts
- Esforço: medio
- Notas: Tela conforme design 1c — nome completo, email, senha, confirmar senha. Zod + RHF. Validação de senha mínima e confirmação. Link "Já tem conta? Entrar".

## T-017 — Frontend: página de gestão de usuários [concluida]

- Refs: US-010, AC-026, AC-027, AC-028, AC-029, AC-030, AC-031
- Arquivos: client/src/pages/users/UsersPage.tsx, client/src/lib/api/users.ts
- Esforço: alto
- Notas: Summary cards (Total de Membros, Gestores, Pesquisadores), tabela com nome/email/papel/status/ações, dialog de convite com email e papel. Alterar papel via select inline, revogar com botão. Marca "(você)" sem ações.

## T-018 — Frontend: página admin de instituições [pendente]

- Refs: US-009, AC-021
- Arquivos: client/src/pages/admin/AdminInstitutionsPage.tsx, client/src/components/admin/InstitutionCard.tsx
- Esforço: medio
- Notas: Tela conforme design 0 — grid de cards com avatar, nome, cidade, status, stats (labs, equipamentos), botões Editar/Entrar. TopBar com badge "Global Admin". Visível apenas para admins. Depende de T-014.

## T-019 — Frontend: atualizações no layout (sidebar, topbar, institution switcher) [concluida]

- Refs: US-009, US-010, AC-021
- Arquivos: client/src/components/layout/AppLayout.tsx
- Esforço: medio
- Notas: Nome do usuário e botão logout no topbar. Sidebar: filtra itens por role (Instituições admin-only, Usuários gestor-only). InstitutionSwitcher mostra apenas instituições do usuário. Depende de T-014.

## T-020 — Testes de integração (auth + autorização + cross-tenant) [pendente]

- Refs: AC-014, AC-015, AC-016, AC-017, AC-018, AC-019, AC-021, AC-022, AC-023, AC-024, AC-025, AC-026, AC-027, AC-028, AC-029, AC-030, AC-031, AC-032, AC-033
- Arquivos: server/src/test/java/com/example/carboncalculator/controllers/AuthControllerTest.java, server/src/test/java/com/example/carboncalculator/controllers/UserControllerTest.java, server/src/test/java/com/example/carboncalculator/controllers/LaboratoryControllerAuthTest.java
- Esforço: alto
- Notas: Testcontainers + PostgreSQL real. Testa fluxos completos de auth, @PreAuthorize em cada endpoint, validação de vínculo cross-tenant.
