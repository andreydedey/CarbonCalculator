# TDD — Registro por Convite (Invite-Only Registration)

| Campo            | Valor                                                    |
| ---------------- | -------------------------------------------------------- |
| Tech Lead        | Andrey Dedey                                             |
| TDD de origem    | `docs/tdds/01-acesso-e-papeis.md`                        |
| Status           | Draft                                                    |
| Criado em        | 2026-10-07                                               |
| Atualizado em    | 2026-10-09                                               |

---

## Contexto

O TDD-01 definiu o sistema de autenticação e papéis. A implementação atual permite que **qualquer pessoa** se registre em `POST /auth/register` — mesmo sem ter sido convidada por nenhum gestor. Isso cria contas órfãs (sem vínculo com instituição) e expõe uma superfície de ataque desnecessária.

Como o modelo do sistema é **baseado em convite** (o gestor convida membros pela tela de Gestão de Usuários), o registro aberto não faz sentido. Este TDD fecha o ciclo: o registro passa a ser acessível **somente** por quem possui um token de convite válido.

### O que já existe

- Autenticação JWT com access token (1h) e refresh token (7d em httpOnly cookie)
- Entidades `AppUser` e `UserInstitution` com status PENDING/ACTIVE
- Fluxo de convite: gestor envia email → cria `UserInstitution(status=PENDING, userEmail=<email>)`
- Na hora do registro, `activatePendingInvitations()` ativa vínculos pendentes por matching de email
- Frontend com páginas `/login` e `/register` públicas
- Google OAuth2 implementado com ativação condicional (`@ConditionalOnExpression`) e `prompt=select_account`

### O que muda

O registro deixa de ser aberto e passa a exigir um **token de convite criptográfico**. O convite gera um token que o gestor copia e envia manualmente (sem dependência de SMTP). O convidado acessa `/register?token=<token>`, preenche nome e senha, e tem a conta criada com membership já ativa.

---

## Definicao do Problema

### Problemas que estamos resolvendo

- **Contas órfãs**: qualquer pessoa pode criar conta sem pertencer a nenhuma instituição, gerando registros inúteis no banco e UX confusa (dashboard vazio)
- **Superfície de ataque aberta**: o endpoint `POST /auth/register` é público e sem rate limiting, permitindo criação em massa de contas
- **Fluxo desconectado**: o convite e o registro são independentes — o gestor convida um email, mas qualquer pessoa (inclusive com outro email) pode se registrar livremente

### Por que agora

Este é um pré-requisito para a segurança mínima do sistema. Não faz sentido implementar novas features com o registro aberto.

### Impacto de não resolver

- Banco de dados acumula usuários sem utilidade
- Possibilidade de abuso (criação em massa de contas)
- Experiência confusa para quem se registra sem convite

---

## Escopo

### Dentro do escopo

- Geração de token de convite criptográfico no backend (SecureRandom, 20 bytes, Base64 URL-safe)
- Armazenamento do hash SHA-256 do token na tabela `user_institution` (nova coluna)
- Expiração configurável do token (padrão: 7 dias)
- Endpoint de validação de token: `GET /auth/invitations/{token}/validate`
- Endpoint de aceitação de convite: `POST /auth/invitations/{token}/accept`
- Remoção do endpoint `POST /auth/register` aberto
- Frontend: página `/register` só funciona com `?token=<token>` válido
- Frontend: após convite, dialog mostra link copiável para o gestor
- Frontend: email preenchido e bloqueado no formulário de registro (vem do convite)
- Migração de convites PENDING existentes (gerar tokens retroativamente)
- Google OAuth com validação de convite pendente (rejeitar se não houver convite)

### Fora do escopo

- Envio automático de email (SMTP/SendGrid) — o gestor copia o link manualmente
- ~~Expiração e reenvio de convite pela UI~~ — **Implementado:** botão "Reenviar" para membros PENDING gera novo token e reabre o dialog com o link (`POST /users/{id}/resend-invite`)
- Rate limiting no endpoint de aceitação (defesa em profundidade, mas não crítico para TCC)
- Recuperação de senha

---

## Solução Técnica

### Visão geral do fluxo

```
Gestor (tela /users)
  │
  │ POST /users/invite { email, role }
  ▼
Backend:
  1. Gera token: SecureRandom(20 bytes) → Base64 URL-safe
  2. Calcula SHA-256(token) → salva em user_institution.invite_token_hash
  3. Define user_institution.invite_expires_at = now + 7 dias
  4. Retorna { ..., inviteLink: "/register?token=<raw_token>" }
  │
  ▼
UI mostra dialog com link copiável:
  ┌──────────────────────────────────────────────┐
  │  https://app.com/register?token=abc123...    │  [Copiar]
  └──────────────────────────────────────────────┘
  │
  │ Gestor copia e envia por WhatsApp/email/etc
  ▼
Convidado acessa /register?token=abc123...
  │
  │ GET /auth/invitations/{token}/validate
  ▼
Backend:
  1. SHA-256(token) → busca em user_institution
  2. Valida: status=PENDING, não expirado
  3. Retorna { email, role, institutionName }
  │
  ▼
Frontend exibe formulário:
  - Email (preenchido, readonly)
  - Nome
  - Senha / Confirmar senha
  │
  │ POST /auth/invitations/{token}/accept { name, password }
  ▼
Backend (transacional):
  1. SHA-256(token) → busca novamente, valida status e expiração
  2. Cria AppUser { name, email, passwordHash }
  3. Atualiza UserInstitution: user=<novo>, status=ACTIVE, limpa token
  4. Ativa outros convites PENDING para o mesmo email (outras instituições)
  5. Gera JWT + refresh cookie
  6. Retorna AuthResponse
  │
  ▼
Convidado logado automaticamente → /dashboard
```

### Modelo de dados — alterações

**Tabela `user_institution`** — novas colunas:

| Coluna               | Tipo                | Restrições                                      |
| -------------------- | ------------------- | ----------------------------------------------- |
| `invite_token_hash`  | `VARCHAR(64)`       | NULL (preenchido apenas para convites PENDING)   |
| `invite_expires_at`  | `TIMESTAMP WITH TZ` | NULL (preenchido apenas para convites PENDING)   |

**Índice:**
- `user_institution(invite_token_hash)` — busca por token na validação/aceitação

**Migration**: nova migration Flyway adicionando as duas colunas e o índice. Convites PENDING existentes receberão tokens gerados retroativamente (migration de dados).

### API — novos endpoints

**Validar token de convite**

```
GET /auth/invitations/{token}/validate
```

- **Público** (não requer autenticação)
- Calcula SHA-256(token), busca `UserInstitution` com esse hash
- Valida: `status = PENDING`, `invite_expires_at > now`
- Resposta 200:

```json
{
  "email": "pesquisador@ufpa.br",
  "role": "RESEARCHER",
  "institutionName": "UFPA"
}
```

- Resposta 400 (token inválido, expirado, ou já consumido):

```json
{
  "message": "Convite inválido ou expirado."
}
```

**Aceitar convite (registro)**

```
POST /auth/invitations/{token}/accept
```

- **Público** (não requer autenticação)
- Request:

```json
{
  "name": "João Silva",
  "password": "senhaSegura123"
}
```

- Resposta 201:

```json
{
  "accessToken": "eyJhbG...",
  "user": {
    "id": "550e8400-...",
    "name": "João Silva",
    "email": "pesquisador@ufpa.br",
    "admin": false,
    "institutions": [
      {
        "institutionId": "660e8400-...",
        "name": "UFPA",
        "role": "RESEARCHER",
        "status": "ACTIVE"
      }
    ]
  }
}
```

- Resposta 400: token inválido/expirado
- Resposta 409: email já registrado (usuário já existe)
- Também define o refresh_token como httpOnly cookie (mesmo padrão do login atual)

### API — alterações em endpoints existentes

**`POST /users/invite`** — resposta alterada

A resposta passa a incluir o link de convite (somente para convites PENDING, ou seja, quando o usuário ainda não existe):

```json
{
  "id": "880e8400-...",
  "email": "novo.pesquisador@ufpa.br",
  "role": "RESEARCHER",
  "status": "PENDING",
  "inviteLink": "/register?token=abc123..."
}
```

Quando o usuário já existe (status=ACTIVE imediato), `inviteLink` é `null`.

**`POST /auth/register`** — removido

Este endpoint deixa de existir. Tentativas de acesso retornam 404.

### Geração e armazenamento do token

**Geração:**
- `SecureRandom` com 20 bytes (160 bits de entropia)
- Codificação Base64 URL-safe sem padding
- Resultado: string de ~27 caracteres, segura para URLs

**Armazenamento:**
- Apenas o hash SHA-256 do token é salvo no banco
- O token bruto é retornado **uma única vez** na resposta do convite
- Se o banco for comprometido, os tokens não podem ser reconstruídos

**Validação:**
- SHA-256(token recebido) → comparado com `invite_token_hash` no banco
- Verificação de `invite_expires_at > now()`
- Verificação de `status = PENDING`

**Consumo (atômico):**
- Dentro de `@Transactional`: criar usuário + atualizar membership + limpar token
- Após consumo: `invite_token_hash = NULL`, `invite_expires_at = NULL`, `status = ACTIVE`

### Google OAuth com convite (implementado)

O Google OAuth segue regras complementares ao convite:

1. Usuário clica "Continuar com Google" na tela de login
2. Spring Security redireciona para Google com `prompt=select_account` (evita cache de conta após revogação)
3. No callback (`OAuth2LoginSuccessHandler`), o backend verifica:
   - **Novo usuário:** busca convites PENDING pelo email do Google. Se não há convite, redireciona para `/login?error=no_invite`. Se há, cria o `AppUser` e ativa as memberships pendentes.
   - **Usuário existente:** verifica se tem membership ACTIVE (ou é admin). Se não, redireciona com erro.
4. Se convite PENDING tem role `ADMIN`, o usuário é promovido a admin global e o role é rebaixado para `MANAGER`
5. Gera JWT + refresh cookie e redireciona para o frontend (`/oauth/callback?token=<jwt>`)

**Ativação condicional:** o OAuth2 só é ativado quando `GOOGLE_CLIENT_ID` está configurado (`@ConditionalOnExpression`). Sem a variável, a app funciona normalmente com login por senha, e os testes rodam sem credenciais do Google.

### Frontend — alterações

**Página `/register`:**
- Extrai `token` dos query params
- Se não houver token: exibe mensagem "Você precisa de um convite para se registrar" com link para `/login`
- Se houver token: chama `GET /auth/invitations/{token}/validate`
  - Se inválido/expirado: exibe mensagem de erro com link para `/login`
  - Se válido: exibe formulário com email preenchido (readonly), nome e senha
- Submit chama `POST /auth/invitations/{token}/accept`
- Sucesso: login automático e redirect para `/dashboard`

**Página `/login`:**
- Remove o link "Criar conta" / "Registrar-se" (já que não há registro aberto)
- Adiciona texto: "Solicite acesso a um gestor da sua instituição"

**Tela de Gestão de Usuários (`/users`):**
- Após convite bem-sucedido, exibe dialog com o link de convite e botão "Copiar"
- O link é composto por `window.location.origin + inviteLink`

**Auth API client (`lib/api/auth.ts`):**
- Remove função `register()`
- Adiciona `validateInvite(token)` e `acceptInvite(token, data)`

**AuthContext:**
- Remove método `register()`
- Adiciona método `acceptInvite(token, name, password)`

**Auth schemas (`lib/schemas/authSchemas.ts`):**
- Schema de registro perde o campo `email` (vem do convite)
- Mantém: `name`, `password`, `confirmPassword`

---

## Riscos

| Risco | Impacto | Probabilidade | Mitigação |
|-------|---------|---------------|-----------|
| Token de convite vazado permite registro não autorizado | Médio | Baixa | Token é de uso único + expira em 7 dias; após consumo, é invalidado |
| Brute force no token de convite | Baixo | Muito baixa | 160 bits de entropia (2^160 combinações); computacionalmente inviável |
| Convites PENDING existentes ficam sem token após migração | Médio | Certa | Migration gera tokens retroativamente; gestores precisam recopiar links |
| Gestor perde o link antes de enviar | Baixo | Média | Botão "Reenviar" na tabela de membros PENDING regenera o token e reabre o dialog com o link |
| Email do Google não coincide com email do convite | Baixo | Baixa | Backend valida que o email OAuth coincide; se divergir, rejeita |

---

## Plano de Implementação

| Fase | Tarefa | Descrição |
|------|--------|-----------|
| **1 — Banco** | Migration | Adicionar colunas `invite_token_hash` e `invite_expires_at` em `user_institution`; índice em `invite_token_hash`; gerar tokens para convites PENDING existentes |
| **2 — Backend** | Token service | Classe utilitária para gerar token (SecureRandom) e calcular hash (SHA-256) |
| **2 — Backend** | Alterar `UserService.invite()` | Gerar token, salvar hash, retornar `inviteLink` na resposta |
| **2 — Backend** | Endpoints de convite | `GET /auth/invitations/{token}/validate` e `POST /auth/invitations/{token}/accept` |
| **2 — Backend** | Remover `/auth/register` | Remover endpoint e `RegisterRequest` DTO |
| **2 — Backend** | SecurityConfig | Permitir novos endpoints públicos; remover `/auth/register` da whitelist |
| **3 — Frontend** | Refatorar RegisterPage | Exigir token, validar antes de mostrar formulário, email readonly |
| **3 — Frontend** | Refatorar LoginPage | Remover link de registro, adicionar texto informativo |
| **3 — Frontend** | Dialog de link de convite | Mostrar link copiável após convite na UsersPage |
| **3 — Frontend** | Auth API/Context | Remover `register()`, adicionar `validateInvite()` e `acceptInvite()` |
| **4 — Testes** | Testes de integração | Token válido → registro; token expirado → 400; token já consumido → 400; email duplicado → 409; sem token → 400 |

---

## Considerações de Segurança

- **Token hashing**: o token bruto nunca é persistido; somente o SHA-256 fica no banco. Comprometimento do banco não expõe tokens válidos
- **Uso único**: transição atômica `PENDING → ACTIVE` dentro de `@Transactional` impede reuso
- **Expiração**: tokens expiram em 7 dias; convites expirados são tratados como inválidos
- **Entropia**: 160 bits de SecureRandom tornam brute force computacionalmente inviável
- **Sem registro público**: remoção do endpoint aberto elimina criação de contas não autorizadas
- **HTTPS**: tokens trafegam na URL (query param), que é protegida pelo TLS. Em produção, HTTPS é obrigatório

---

## Estratégia de Testes

| Tipo | Cenário | Resultado esperado |
|------|---------|-------------------|
| Integração | Convite gera token e retorna inviteLink | 201 + link válido |
| Integração | Validar token válido | 200 + email, role, institutionName |
| Integração | Validar token expirado | 400 |
| Integração | Validar token já consumido | 400 |
| Integração | Validar token inexistente | 400 |
| Integração | Aceitar convite com token válido | 201 + JWT + user com membership ACTIVE |
| Integração | Aceitar convite com email já registrado | 409 |
| Integração | Aceitar convite com token expirado | 400 |
| Integração | Aceitar convite ativa outros convites PENDING do mesmo email | Todas as memberships ficam ACTIVE |
| Integração | POST /auth/register (antigo) | 404 |
| Integração | GET /register sem token no frontend | Mensagem de erro, sem formulário |
| Integração | Revogar convite PENDING invalida token | Convite deletado do banco |

---

## Questões Resolvidas

| Questão | Decisão |
|---------|---------|
| Envio de email é necessário? | Não. O gestor copia o link manualmente. SMTP pode ser adicionado depois sem mudar a lógica |
| O token vai na URL (query param)? | Sim. É protegido por HTTPS e é de uso único. Mesmo padrão usado por Slack, Linear, etc. |
| Precisa de verificação de email separada? | Não. O token de convite já prova que o convidado recebeu o link (prova de posse do canal). Com Google OAuth, o Google já verificou |
| UUID.randomUUID() é suficiente para o token? | Não. SecureRandom com 20 bytes oferece 160 bits de entropia vs 122 bits do UUID v4. É o recomendado pela OWASP |
| O que acontece com convites PENDING existentes? | A migration gera tokens retroativamente. Gestores precisam acessar `/users`, copiar os novos links e reenviar |
| O `/register` público some completamente? | Sim. A rota continua existindo no frontend mas só funciona com token. O endpoint backend `POST /auth/register` é removido |
