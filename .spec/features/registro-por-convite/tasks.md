# Tasks: Registro por convite

> feature: registro-por-convite

## T-060 — Migration: colunas de token no user_institution [concluida]
- Refs: US-047, AC-155, AC-156, AC-157
- Arquivos: server/src/main/resources/db/migration/V24__add_invite_token_to_user_institution.sql
- Notas: adicionar invite_token_hash (VARCHAR 64), invite_expires_at (TIMESTAMPTZ), índice em invite_token_hash. Gerar tokens retroativamente para convites PENDING existentes.

## T-061 — Backend: InviteTokenService + alterar UserInstitution entity [concluida]
- Refs: US-047, AC-155, AC-156, AC-157, AC-158
- Arquivos: server/src/main/java/com/example/carboncalculator/services/InviteTokenService.java, server/src/main/java/com/example/carboncalculator/entities/UserInstitution.java
- Notas: classe utilitária para gerar token (SecureRandom 20 bytes + Base64 URL-safe) e calcular SHA-256. Adicionar campos inviteTokenHash e inviteExpiresAt na entidade.

## T-062 — Backend: alterar UserService.invite() para gerar token [concluida]
- Refs: US-047, AC-155, AC-156, AC-157, AC-158
- Arquivos: server/src/main/java/com/example/carboncalculator/services/UserService.java, server/src/main/java/com/example/carboncalculator/controllers/UserController.java, server/src/main/java/com/example/carboncalculator/dto/UserMemberDTO.java
- Notas: ao criar convite PENDING, gerar token via InviteTokenService, salvar hash, definir expiresAt. Retornar inviteLink no DTO. Se usuário já existe (ACTIVE imediato), inviteLink = null. Depende de T-060 e T-061.

## T-063 — Backend: endpoints de validação e aceitação de convite [concluida]
- Refs: US-048, AC-159, AC-160, AC-161, AC-162, AC-163, AC-164
- Arquivos: server/src/main/java/com/example/carboncalculator/controllers/AuthController.java, server/src/main/java/com/example/carboncalculator/services/AuthService.java, server/src/main/java/com/example/carboncalculator/config/SecurityConfig.java
- Notas: GET /auth/invitations/{token}/validate (público) e POST /auth/invitations/{token}/accept (público). Aceitar é @Transactional: validar token, criar AppUser, ativar membership, ativar outros PENDING do mesmo email, gerar JWT. Depende de T-061.

## T-064 — Backend: remover endpoint POST /auth/register [concluida]
- Refs: US-049, AC-167
- Arquivos: server/src/main/java/com/example/carboncalculator/controllers/AuthController.java, server/src/main/java/com/example/carboncalculator/services/AuthService.java, server/src/main/java/com/example/carboncalculator/config/SecurityConfig.java
- Notas: remover o método register do controller e do service. Remover /auth/register da whitelist do SecurityConfig. Depende de T-063 (novo endpoint deve existir antes de remover o antigo).

## T-065 — Frontend: refatorar RegisterPage para exigir token [concluida]
- Refs: US-048, AC-165, AC-166
- Arquivos: client/src/pages/auth/RegisterPage.tsx, client/src/lib/api/auth.ts, client/src/context/AuthContext.tsx, client/src/lib/schemas/authSchemas.ts
- Notas: extrair token da URL, validar via API, mostrar formulário com email readonly ou mensagem de erro. Remover register() do AuthContext, adicionar validateInvite() e acceptInvite(). Depende de T-063.

## T-066 — Frontend: refatorar LoginPage (remover link de registro) [concluida]
- Refs: US-049, AC-168
- Arquivos: client/src/pages/auth/LoginPage.tsx
- Notas: remover link "Criar conta", adicionar texto "Solicite acesso a um gestor da sua instituição". Sem dependência de backend.

## T-067 — Frontend: dialog de link de convite na UsersPage [concluida]
- Refs: US-050, AC-169, AC-170
- Arquivos: client/src/pages/users/UsersPage.tsx, client/src/lib/api/users.ts
- Notas: após convite PENDING, mostrar dialog com link copiável (window.location.origin + inviteLink). Botão "Copiar" usa navigator.clipboard.writeText(). Depende de T-062 (resposta do invite inclui inviteLink).

## T-068 — Testes de integração backend [concluida]
- Refs: AC-155, AC-156, AC-157, AC-158, AC-159, AC-160, AC-161, AC-162, AC-163, AC-164, AC-167
- Arquivos: server/src/test/java/com/example/carboncalculator/InviteRegistrationIntegrationTest.java
- Esforço: alto
- Notas: testar todo o fluxo: convite gera token, validação, aceitação, token expirado, token consumido, email duplicado, ativação cross-institution, endpoint antigo removido. Depende de T-060 a T-064.

## T-069 — Testes frontend [concluida]
- Refs: AC-165, AC-166, AC-168, AC-169, AC-170
- Arquivos: client/src/pages/auth/RegisterPage.test.tsx, client/src/pages/auth/LoginPage.test.tsx, client/src/pages/users/UsersPage.test.tsx
- Esforço: medio
- Notas: testar RegisterPage com/sem token, LoginPage sem link de registro, UsersPage dialog de link. Depende de T-065 a T-067.
