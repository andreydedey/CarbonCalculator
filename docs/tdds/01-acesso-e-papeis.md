# TDD — Acesso, Papéis e Isolamento entre Instituições

| Campo            | Valor                                          |
| ---------------- | ---------------------------------------------- |
| Tech Lead        | Andrey Dedey                                   |
| PRD de origem    | `docs/prds/01-acesso-e-papeis.md`              |
| ADRs relevantes  | ADR-001 (stack), ADR-004 (multi-tenancy RLS)   |
| Status           | Draft                                          |
| Criado em        | 2026-09-20                                     |
| Atualizado em    | 2026-09-20                                     |

---

## Contexto

O sistema já tem multi-tenancy funcionando via RLS (ADR-004): o header `X-Institution-Id` define o tenant ativo, e o PostgreSQL filtra os dados automaticamente. Porém não existe autenticação — qualquer pessoa com acesso à URL pode operar qualquer instituição. Este TDD adiciona a camada de identidade e autorização.

O design (Pencil) define três telas: Login (1a), Registro (1c) e Gestão de Usuários (1b), além de uma visão de administração global de instituições (0 – Instituições Global Admin).

### Decisões resolvidas

- **Um usuário pode pertencer a mais de uma instituição.** O modelo usa uma tabela associativa `user_institution` com papel por vínculo. Na prática, isso serve pesquisadores que atuam em mais de uma universidade.
- **Resultados agregados podem ser públicos, mas opt-in.** A instituição escolhe se compartilha seus resultados. Isso será implementado como um flag `public_results` na tabela `institution` — mas a funcionalidade de exibição pública fica para um PRD futuro. Neste TDD, apenas adicionamos a coluna.
- **Google OAuth será implementado nesta fase.** O design mostra "Continuar com Google" tanto no login quanto no registro. Usaremos Spring Security OAuth2 Client.
- **Autocadastro de usuários é aberto, mas sem acesso até ser vinculado.** Qualquer pessoa pode criar conta (tela 1c), mas sem vínculo a uma instituição não acessa nenhum dado. O gestor convida por email; se o email já tem conta, o vínculo é criado direto; se não, o convite fica pendente até o registro.

## Definição do Problema

Sem autenticação, o `X-Institution-Id` é confiado cegamente — qualquer client HTTP pode enviar qualquer UUID e operar sobre os dados daquela instituição. Além disso, não há distinção entre quem pode editar e quem pode apenas consultar. O sistema precisa:

1. Verificar a identidade do usuário (autenticação)
2. Garantir que ele só acessa instituições às quais está vinculado (autorização)
3. Diferenciar permissões dentro da instituição (gestor vs consulta)
4. Ter um papel administrativo global para gerenciar instituições e dados compartilhados (fatores de emissão)

**O que acontece se não resolvermos:**
- Qualquer pessoa com acesso à rede pode ler e alterar dados de qualquer instituição
- Não há como restringir ações destrutivas a gestores
- Não há como dar acesso somente-leitura para coordenadores

## Escopo

### Dentro do escopo

- Entidades `AppUser` e `UserInstitution` no banco de dados com migrations Flyway
- Autenticação por email/senha com JWT (stateless)
- Autenticação via Google OAuth2
- Registro de novos usuários (nome, email, senha)
- Três papéis: `ADMIN` (global), `GESTOR` (por instituição), `PESQUISADOR` (por instituição)
- `@PreAuthorize` em todos os endpoints existentes e futuros
- Tela de login (1a) com email/senha e Google
- Tela de registro (1c) com formulário e Google
- Tela de gestão de usuários (1b) com tabela, convite, alteração de papel e revogação
- Tela de administração global de instituições (0) para admins
- Interceptor HTTP no frontend que envia JWT no header `Authorization`
- Validação no `TenantFilter`: após autenticação, verificar que o usuário tem vínculo com a instituição do header
- Coluna `public_results` em `institution` (apenas a coluna, sem funcionalidade de exibição)

### Fora do escopo

- Recuperação de senha ("Esqueceu sua senha?" — link presente no design, mas funcionalidade adiada)
- Exibição pública de resultados agregados (apenas o flag opt-in)
- Integração com SSO institucional das universidades
- Trilha de auditoria detalhada
- Hierarquia abaixo da instituição (departamentos, cursos)
- Compartilhamento parcial de dados entre instituições

---

## Solução Técnica

### Visão geral da arquitetura

```
┌─────────────────────────────────────────────────────────┐
│  Frontend (React + Vite + shadcn/ui)                    │
│                                                         │
│  AuthContext  ──→  pages/ (Login, Registro, Usuários)   │
│  (JWT token)      InstitutionContext  ──→  pages/       │
│                                                         │
│  Axios interceptor: Authorization: Bearer <jwt>         │
│                     X-Institution-Id: <uuid>            │
└──────────────────────────┬──────────────────────────────┘
                           │ HTTP (JSON)
┌──────────────────────────▼──────────────────────────────┐
│  Backend (Spring Boot + Spring Security)                │
│                                                         │
│  SecurityFilterChain (JWT filter)                       │
│    → TenantFilter (valida vínculo user↔institution)     │
│      → Controller (@PreAuthorize)                       │
│        → Service (regras de negócio)                    │
│                                                         │
│  OAuth2 login (Google) → gera JWT                       │
└──────────────────────────┬──────────────────────────────┘
                           │ JDBC
┌──────────────────────────▼──────────────────────────────┐
│  PostgreSQL + RLS                                       │
│  app_user / user_institution / institution / laboratory  │
└─────────────────────────────────────────────────────────┘
```

### Modelo de dados

**Tabela `app_user`** (nome `app_user` para evitar conflito com `user` reservado no PostgreSQL)

| Coluna          | Tipo                 | Restrições                     |
| --------------- | -------------------- | ------------------------------ |
| `id`            | `UUID`               | PK, gerado automaticamente    |
| `name`          | `VARCHAR(255)`       | NOT NULL                       |
| `email`         | `VARCHAR(255)`       | NOT NULL, UNIQUE               |
| `password_hash` | `VARCHAR(255)`       | NULL (null para OAuth-only)    |
| `is_admin`      | `BOOLEAN`            | NOT NULL, DEFAULT FALSE        |
| `active`        | `BOOLEAN`            | NOT NULL, DEFAULT TRUE         |
| `created_at`    | `TIMESTAMP WITH TZ`  | NOT NULL                       |
| `updated_at`    | `TIMESTAMP WITH TZ`  | NOT NULL                       |

**Tabela `user_institution`**

| Coluna           | Tipo                 | Restrições                             |
| ---------------- | -------------------- | -------------------------------------- |
| `id`             | `UUID`               | PK, gerado automaticamente            |
| `user_id`        | `UUID`               | FK → app_user(id), NOT NULL            |
| `institution_id` | `UUID`               | FK → institution(id), NOT NULL         |
| `role`           | `VARCHAR(20)`        | NOT NULL, CHECK IN ('GESTOR','PESQUISADOR') |
| `status`         | `VARCHAR(20)`        | NOT NULL, CHECK IN ('ACTIVE','PENDING')  |
| `created_at`     | `TIMESTAMP WITH TZ`  | NOT NULL                               |

**Constraint:** UNIQUE(user_id, institution_id) — um usuário tem no máximo um vínculo por instituição.

**Alteração em `institution`:**

| Coluna           | Tipo      | Restrições               |
| ---------------- | --------- | ------------------------ |
| `public_results` | `BOOLEAN` | NOT NULL, DEFAULT FALSE  |

**Índices:**
- `app_user(email)` — UNIQUE, busca por login
- `user_institution(user_id)` — listar instituições do usuário
- `user_institution(institution_id)` — listar membros da instituição

### Autenticação

**JWT (email/senha):**

1. Usuário envia `POST /api/v1/auth/login` com `{ email, password }`
2. Backend valida credenciais, gera JWT assinado com HMAC-SHA256
3. JWT contém: `sub` (user ID), `email`, `name`, `admin` (boolean), `exp` (expiração)
4. Frontend armazena o JWT em memória (`AuthContext`) — não em localStorage (XSS)
5. Axios interceptor envia `Authorization: Bearer <jwt>` em toda requisição
6. Refresh via `POST /api/v1/auth/refresh` com token ainda válido

**JWT config:**
- Access token: 1 hora de validade
- Refresh token: 7 dias (armazenado em httpOnly cookie)
- Chave HMAC configurada via variável de ambiente `JWT_SECRET`

**Google OAuth2:**

1. Frontend redireciona para `/oauth2/authorization/google`
2. Spring Security OAuth2 Client lida com o fluxo
3. No callback, o backend busca/cria o `AppUser` pelo email do Google
4. Gera JWT e redireciona para o frontend com o token

**Registro:**

1. `POST /api/v1/auth/register` com `{ name, email, password }`
2. Valida que email não existe, faz hash da senha com BCrypt
3. Cria `AppUser` e retorna JWT (login automático após registro)
4. Se existem convites pendentes para esse email, os vínculos passam de PENDING para ACTIVE

### Autorização

**Papéis:**

| Papel     | Escopo       | Permissões                                                              |
| --------- | ------------ | ----------------------------------------------------------------------- |
| `ADMIN`   | Global       | CRUD de instituições, edição de fatores de emissão, convite de gestores |
| `GESTOR`  | Instituição  | CRUD de labs, equipamentos, medições, calendário; convite/revogação de membros |
| `PESQUISADOR`| Instituição  | Somente leitura de todos os dados da instituição; acesso a simulações e resultados |

**Hierarquia de papéis (Spring Security `RoleHierarchy`):**

```
ADMIN > GESTOR > PESQUISADOR
```

- **ADMIN** herda todas as permissões de GESTOR e PESQUISADOR. Pode operar em qualquer instituição sem vínculo explícito.
- **GESTOR** herda as permissões de PESQUISADOR. Pode ler e também criar/editar/excluir dados da sua instituição.
- **PESQUISADOR** é o papel base. Somente leitura dos dados da instituição à qual está vinculado.

A hierarquia é configurada como bean no Spring Security:

```java
@Bean
RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.fromHierarchy("""
        ROLE_ADMIN > ROLE_GESTOR
        ROLE_GESTOR > ROLE_PESQUISADOR
    """);
}
```

Isso permite que `@PreAuthorize("hasRole('PESQUISADOR')")` autorize automaticamente GESTOR e ADMIN, sem precisar listar todos os papéis em cada anotação.

**Resolução de authorities:**

| Usuário | `is_admin` | Vínculos | Authorities resultantes (na instituição X) |
| ------- | ---------- | -------- | ------------------------------------------ |
| Carlos  | true       | UFPA/GESTOR | ROLE_ADMIN, ROLE_GESTOR, ROLE_PESQUISADOR |
| Ana     | false      | UFPA/GESTOR | ROLE_GESTOR, ROLE_PESQUISADOR |
| Bruno   | false      | UFPA/PESQUISADOR | ROLE_PESQUISADOR |
| Rafael  | false      | (nenhum) | (sem acesso à instituição X) |

**Regras de `@PreAuthorize`:**

| Endpoint                                | Acesso                                    |
| --------------------------------------- | ----------------------------------------- |
| `POST /auth/login`                      | Público                                   |
| `POST /auth/register`                   | Público                                   |
| `GET /institutions`                     | Autenticado (admin vê todas, outros vêem as suas) |
| `POST /institutions`                    | ADMIN                                     |
| `GET /institutions/{id}`                | ADMIN ou membro da instituição            |
| `PUT /institutions/{id}`                | ADMIN                                     |
| `POST /laboratories`                    | GESTOR da instituição do header           |
| `GET /laboratories`                     | Membro da instituição do header           |
| `PATCH /laboratories/{id}/activate`     | GESTOR da instituição do header           |
| `PATCH /laboratories/{id}/deactivate`   | GESTOR da instituição do header           |
| `DELETE /laboratories/{id}`             | GESTOR da instituição do header           |
| `GET /users` (da instituição)           | GESTOR da instituição do header           |
| `POST /users/invite`                    | GESTOR da instituição do header           |
| `PATCH /users/{id}/role`                | GESTOR da instituição do header           |
| `DELETE /users/{id}` (revogar)          | GESTOR da instituição do header           |

**TenantFilter atualizado:**

O `TenantFilter` existente será modificado para, após extrair o `X-Institution-Id`:
1. Verificar que o usuário autenticado tem um `UserInstitution` ativo para aquela instituição (ou é admin)
2. Se não tiver, retornar `403 Forbidden`
3. Admins podem usar qualquer `X-Institution-Id`

### API REST

**Autenticação**

| Método | Rota                          | Descrição                              | Status |
| ------ | ----------------------------- | -------------------------------------- | ------ |
| POST   | `/api/v1/auth/register`       | Registrar novo usuário                 | 201    |
| POST   | `/api/v1/auth/login`          | Login com email/senha                  | 200    |
| POST   | `/api/v1/auth/refresh`        | Renovar access token                   | 200    |
| GET    | `/api/v1/auth/me`             | Dados do usuário logado                | 200    |

**Contratos de autenticação:**

```json
// POST /api/v1/auth/register
// Request
{
  "name": "Carlos Mendes",
  "email": "carlos.mendes@ufpa.br",
  "password": "senhaSegura123"
}

// Response 201
{
  "accessToken": "eyJhbG...",
  "user": {
    "id": "550e8400-...",
    "name": "Carlos Mendes",
    "email": "carlos.mendes@ufpa.br",
    "admin": false
  }
}
```

```json
// POST /api/v1/auth/login
// Request
{
  "email": "carlos.mendes@ufpa.br",
  "password": "senhaSegura123"
}

// Response 200
{
  "accessToken": "eyJhbG...",
  "user": {
    "id": "550e8400-...",
    "name": "Carlos Mendes",
    "email": "carlos.mendes@ufpa.br",
    "admin": false
  }
}
```

```json
// GET /api/v1/auth/me
// Response 200
{
  "id": "550e8400-...",
  "name": "Carlos Mendes",
  "email": "carlos.mendes@ufpa.br",
  "admin": false,
  "institutions": [
    {
      "institutionId": "660e8400-...",
      "name": "UFPA",
      "role": "GESTOR",
      "status": "ACTIVE"
    }
  ]
}
```

**Gestão de Usuários** (requer header `X-Institution-Id`)

| Método | Rota                              | Descrição                          | Status |
| ------ | --------------------------------- | ---------------------------------- | ------ |
| GET    | `/api/v1/users`                   | Listar membros da instituição      | 200    |
| POST   | `/api/v1/users/invite`            | Convidar usuário por email         | 201    |
| PATCH  | `/api/v1/users/{id}/role`         | Alterar papel do membro            | 200    |
| DELETE | `/api/v1/users/{id}`              | Revogar acesso do membro           | 204    |

**Contratos de gestão de usuários:**

```json
// GET /api/v1/users
// Header: X-Institution-Id: 660e8400-...
// Response 200
[
  {
    "id": "550e8400-...",
    "name": "Carlos Mendes",
    "email": "carlos.mendes@ufpa.br",
    "role": "GESTOR",
    "status": "ACTIVE"
  },
  {
    "id": "770e8400-...",
    "name": "Rafael Souza",
    "email": "rafael.souza@ufpa.br",
    "role": "PESQUISADOR",
    "status": "PENDING"
  }
]
```

```json
// POST /api/v1/users/invite
// Header: X-Institution-Id: 660e8400-...
// Request
{
  "email": "novo.pesquisador@ufpa.br",
  "role": "PESQUISADOR"
}

// Response 201
{
  "id": "880e8400-...",
  "email": "novo.pesquisador@ufpa.br",
  "role": "PESQUISADOR",
  "status": "PENDING"
}
```

```json
// PATCH /api/v1/users/{id}/role
// Header: X-Institution-Id: 660e8400-...
// Request
{
  "role": "GESTOR"
}

// Response 200
{
  "id": "770e8400-...",
  "name": "Rafael Souza",
  "email": "rafael.souza@ufpa.br",
  "role": "GESTOR",
  "status": "ACTIVE"
}
```

**Regras de negócio na API:**
- O gestor não pode revogar o próprio acesso (design mostra "(você)" sem botões de ação)
- O gestor não pode alterar o próprio papel
- Convite duplicado (mesmo email + mesma instituição) retorna `409 Conflict`
- Convite para email já vinculado à instituição retorna `409 Conflict`
- Login com credenciais inválidas retorna `401 Unauthorized` com mensagem genérica
- Registro com email já existente retorna `409 Conflict`

### Frontend

**Contexto de autenticação:**

- `AuthContext` armazena o JWT em memória (state React) e os dados do usuário
- No carregamento da app, tenta renovar o token via refresh cookie (httpOnly)
- Se não tiver sessão válida, redireciona para `/login`
- Expõe `login()`, `register()`, `logout()`, `user`, `isAuthenticated`

**Rotas protegidas:**

- Rotas públicas: `/login`, `/register`
- Rotas autenticadas: todas as demais (envolvidas por um `<ProtectedRoute>`)
- Rota admin: `/institutions` (visão global, só admin)
- A visão de gestão de usuários será acessível pela sidebar: "Administração > Usuários"

**Páginas (conforme design):**

| Página                      | Rota             | Descrição                                              |
| --------------------------- | ---------------- | ------------------------------------------------------ |
| Login                       | `/login`         | Email/senha + Google OAuth                             |
| Registro                    | `/register`      | Nome, email, senha + Google OAuth                      |
| Gestão de Usuários          | `/users`         | Tabela de membros com convite, alterar papel, revogar  |
| Instituições (Global Admin) | `/institutions`  | Grid de cards com todas as instituições (somente admin) |

**Modificações em componentes existentes:**

- `InstitutionSwitcher`: filtrar para mostrar apenas instituições às quais o usuário está vinculado
- `AppLayout`: exibir nome do usuário + avatar no top bar, botão de logout
- `Sidebar`: adicionar item "Usuários" na seção "Administração" (visível apenas para gestores)
- Axios interceptor: adicionar `Authorization: Bearer <jwt>` além do `X-Institution-Id`

### Dependências (bibliotecas)

**Backend:**
- `spring-boot-starter-security` — já presente
- `spring-boot-starter-oauth2-client` — Google OAuth2
- `io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson` — geração e validação de JWT
- `spring-security-test` — já presente

**Frontend:**
- Nenhuma nova dependência (usa Axios, React Router, Zod, RHF, shadcn/ui já existentes)

---

## Riscos

| Risco | Impacto | Probabilidade | Mitigação |
|-------|---------|---------------|-----------|
| JWT secret vazado expõe todas as sessões | Alto | Baixa | Variável de ambiente, nunca commitada; rotação periódica |
| Google OAuth callback mal configurado em produção | Médio | Média | Documentar configuração do Google Cloud Console; testar em staging |
| Token armazenado em localStorage é vulnerável a XSS | Alto | Média | Armazenar access token apenas em memória; refresh token em httpOnly cookie |
| Gestor remove todos os gestores de uma instituição, ficando sem acesso | Médio | Baixa | Validar que pelo menos um gestor ativo permanece |
| TenantFilter não valida vínculo corretamente, permitindo acesso cross-tenant | Alto | Baixa | Testes de integração explícitos para cenários cross-tenant |

---

## Plano de Implementação

| Fase | Tarefa | Descrição | Estimativa |
|------|--------|-----------|------------|
| **1 — Banco** | Migration V4 — app_user | Criar tabela `app_user` | 0.5d |
| **1 — Banco** | Migration V5 — user_institution | Criar tabela `user_institution` com constraints | 0.5d |
| **1 — Banco** | Migration V6 — public_results | Adicionar coluna `public_results` em `institution` | 0.25d |
| **1 — Banco** | Entities | JPA entities `AppUser` e `UserInstitution` | 0.5d |
| **2 — Auth** | JWT service | Geração, validação e refresh de tokens JWT | 1d |
| **2 — Auth** | SecurityFilterChain | JWT filter, public endpoints, CORS config | 0.5d |
| **2 — Auth** | AuthController | Endpoints de register, login, refresh, me | 1d |
| **2 — Auth** | Google OAuth2 | OAuth2 client config, callback handler, geração de JWT após OAuth | 1d |
| **2 — Auth** | TenantFilter update | Validar vínculo user↔institution após autenticação | 0.5d |
| **3 — Backend** | UserService + Controller | CRUD de membros: listar, convidar, alterar papel, revogar | 1d |
| **3 — Backend** | @PreAuthorize | Anotações em todos os controllers existentes e novos | 0.5d |
| **3 — Backend** | InstitutionService update | Filtrar instituições por vínculo do usuário (exceto admin) | 0.5d |
| **4 — Frontend** | AuthContext + interceptor | Contexto de auth, JWT em memória, Axios interceptor | 1d |
| **4 — Frontend** | Login page | Tela de login conforme design (1a) | 0.5d |
| **4 — Frontend** | Register page | Tela de registro conforme design (1c) | 0.5d |
| **4 — Frontend** | ProtectedRoute | Componente wrapper que redireciona para login | 0.25d |
| **4 — Frontend** | User management page | Tela de gestão de usuários conforme design (1b) | 1d |
| **4 — Frontend** | Admin institutions page | Tela de administração global conforme design (0) | 1d |
| **4 — Frontend** | Layout updates | Avatar/nome no topbar, item Usuários na sidebar, logout | 0.5d |
| **5 — Testes** | Testes de integração | Auth endpoints, @PreAuthorize, cross-tenant validation | 1.5d |
| **5 — Testes** | Testes unitários | JWT service, validações de negócio | 0.5d |

**Estimativa total**: ~13 dias úteis

**Dependências entre fases:**
- Fase 1 desbloqueia Fase 2 e 3
- Fase 2 desbloqueia Fase 3 e 4
- Fase 3 pode rodar em paralelo com Fase 4
- Fase 5 pode começar após Fase 2

---

## Estratégia de Testes

| Tipo | Escopo | Abordagem |
|------|--------|-----------|
| **Integração (backend)** | Auth + autorização + RLS | Requisições HTTP reais com PostgreSQL em container (Testcontainers) |
| **Unitário (backend)** | JWT service, validações | Testes isolados das regras de negócio |

**Cenários críticos a testar:**

**Autenticação:**
- Registro com dados válidos → 201 + JWT + user
- Registro com email duplicado → 409 Conflict
- Login com credenciais válidas → 200 + JWT
- Login com credenciais inválidas → 401 Unauthorized
- Request com JWT expirado → 401 Unauthorized
- Request sem JWT → 401 Unauthorized (exceto rotas públicas)
- Refresh com token válido → 200 + novo access token
- Refresh com token expirado → 401 Unauthorized

**Autorização:**
- Admin acessa `GET /institutions` → vê todas
- Gestor acessa `GET /institutions` → vê apenas as suas
- Gestor acessa `POST /laboratories` com `X-Institution-Id` da sua instituição → 201
- Gestor acessa `POST /laboratories` com `X-Institution-Id` de outra instituição → 403
- Consulta acessa `GET /laboratories` → 200
- Consulta acessa `POST /laboratories` → 403
- Consulta acessa `DELETE /laboratories/{id}` → 403
- Admin cria instituição → 201
- Gestor cria instituição → 403

**Gestão de usuários:**
- Gestor convida usuário → 201 + status PENDING
- Convite duplicado → 409 Conflict
- Gestor altera papel de membro → 200
- Gestor tenta alterar o próprio papel → 400
- Gestor revoga acesso de membro → 204
- Gestor tenta revogar o próprio acesso → 400
- Gestor tenta revogar quando é o último gestor → 400
- Registro de usuário com convite pendente → vínculo ativado automaticamente
