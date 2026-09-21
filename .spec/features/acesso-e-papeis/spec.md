# Spec: Acesso e papéis

> feature: acesso-e-papeis
> status: rascunho

## Contexto

O sistema já isola dados entre instituições via RLS (ADR-004), mas não tem autenticação — qualquer pessoa com acesso à rede pode operar qualquer instituição. Esta feature adiciona identidade (login/registro), autorização por papéis (ADMIN, GESTOR, PESQUISADOR) e gestão de membros por instituição.

## Histórias

### US-006 — Autenticação por email e senha

Como usuário da plataforma, quero fazer login com meu email e senha, para que apenas eu acesse minha conta.

#### AC-014 — Login com credenciais válidas retorna JWT

- **Dado** que tenho uma conta com email "carlos@ufpa.br" e senha cadastrada
- **Quando** faço login com email e senha corretos
- **Então** recebo um token de acesso JWT e os dados do meu perfil (resposta 200)

#### AC-015 — Login com credenciais inválidas é recusado

- **Dado** que informo um email ou senha incorretos
- **Quando** tento fazer login
- **Então** a autenticação é recusada com mensagem genérica, sem revelar se o email existe (resposta 401)

#### AC-016 — Acesso sem autenticação é bloqueado

- **Dado** que não estou autenticado (sem token JWT)
- **Quando** tento acessar qualquer endpoint protegido
- **Então** o acesso é negado (resposta 401)

### US-007 — Registro de novo usuário

Como pessoa convidada, quero criar minha conta na plataforma, para que eu possa acessar a instituição à qual fui vinculada.

#### AC-017 — Registro com dados válidos cria conta e retorna JWT

- **Dado** que informo nome, email e senha válidos
- **Quando** submeto o registro
- **Então** minha conta é criada e recebo um token JWT com login automático (resposta 201)

#### AC-018 — Registro com email já existente é recusado

- **Dado** que já existe uma conta com o email "carlos@ufpa.br"
- **Quando** tento registrar com o mesmo email
- **Então** o registro é recusado com aviso de email já em uso (resposta 409)

#### AC-019 — Convites pendentes são ativados no registro

- **Dado** que um gestor convidou "ana@ufpa.br" como PESQUISADOR na UFPA
- **Quando** Ana cria sua conta com esse email
- **Então** o vínculo com a UFPA passa automaticamente de PENDING para ACTIVE, e Ana já acessa os dados da instituição

### US-008 — Autenticação via Google

Como usuário, quero fazer login ou me registrar usando minha conta Google, para não precisar criar uma senha.

#### AC-020 — Google OAuth cria ou vincula conta e retorna JWT

- **Dado** que inicio o fluxo de login via Google
- **Quando** autorizo o acesso no Google
- **Então** o sistema cria minha conta (se não existir) ou vincula ao email existente, e recebo um token JWT

### US-009 — Autorização por papéis

Como administrador ou gestor, quero que cada endpoint tenha controle de acesso baseado em papéis, para que ninguém execute ações fora de sua permissão.

#### AC-021 — Admin acessa e gerencia todas as instituições

- **Dado** que sou um usuário com `is_admin = true`
- **Quando** listo as instituições
- **Então** vejo todas as instituições cadastradas e posso criar, editar e entrar em qualquer uma

#### AC-022 — Gestor pode criar, editar e excluir dados da sua instituição

- **Dado** que sou GESTOR da instituição UFPA
- **Quando** crio, edito ou excluo um laboratório com o header da UFPA
- **Então** a operação é executada com sucesso

#### AC-023 — Pesquisador tem acesso somente leitura

- **Dado** que sou PESQUISADOR da instituição UFPA
- **Quando** tento criar, editar ou excluir um laboratório
- **Então** a operação é negada (resposta 403), mas consigo listar e visualizar os dados

#### AC-024 — Usuário sem vínculo não acessa dados da instituição

- **Dado** que estou autenticado mas não tenho vínculo com a instituição X
- **Quando** envio uma requisição com `X-Institution-Id` da instituição X
- **Então** o acesso é negado (resposta 403)

#### AC-025 — Hierarquia de papéis funciona corretamente

- **Dado** que a hierarquia é ADMIN > GESTOR > PESQUISADOR
- **Quando** um endpoint exige papel PESQUISADOR
- **Então** tanto PESQUISADOR quanto GESTOR e ADMIN são autorizados

### US-010 — Gestão de membros da instituição

Como gestor institucional, quero convidar colegas e gerenciar seus papéis, para que a manutenção do cadastro não dependa só de mim.

#### AC-026 — Gestor convida usuário por email

- **Dado** que sou GESTOR da UFPA
- **Quando** convido "bruno@ufpa.br" com papel PESQUISADOR
- **Então** o convite é criado com status PENDING (resposta 201)

#### AC-027 — Convite duplicado é recusado

- **Dado** que "bruno@ufpa.br" já tem vínculo (ACTIVE ou PENDING) com a UFPA
- **Quando** tento convidá-lo novamente
- **Então** o convite é recusado com aviso de vínculo já existente (resposta 409)

#### AC-028 — Gestor altera papel de membro

- **Dado** que Bruno é PESQUISADOR na UFPA
- **Quando** altero seu papel para GESTOR
- **Então** o papel é atualizado (resposta 200) e Bruno passa a ter permissões de gestão

#### AC-029 — Gestor revoga acesso de membro

- **Dado** que Bruno é membro da UFPA
- **Quando** revogo seu acesso
- **Então** o vínculo é removido (resposta 204) e Bruno não acessa mais os dados da UFPA

#### AC-030 — Gestor não pode revogar o próprio acesso

- **Dado** que sou GESTOR da UFPA
- **Quando** tento revogar meu próprio acesso
- **Então** a operação é recusada (resposta 400)

#### AC-031 — Gestor não pode alterar o próprio papel

- **Dado** que sou GESTOR da UFPA
- **Quando** tento alterar meu próprio papel
- **Então** a operação é recusada (resposta 400)

### US-011 — Renovação de sessão

Como usuário logado, quero que minha sessão seja renovada automaticamente, para não precisar fazer login a cada hora.

#### AC-032 — Refresh token renova o access token

- **Dado** que meu access token expirou mas tenho um refresh token válido (httpOnly cookie)
- **Quando** faço uma requisição de refresh
- **Então** recebo um novo access token (resposta 200)

#### AC-033 — Refresh com token expirado é recusado

- **Dado** que meu refresh token expirou (> 7 dias)
- **Quando** tento renovar o access token
- **Então** a renovação é recusada (resposta 401) e preciso fazer login novamente

## Fora de escopo

- Recuperação de senha ("Esqueceu sua senha?" — link presente no design, funcionalidade adiada)
- Exibição pública de resultados agregados (apenas o flag `public_results` é adicionado)
- Integração com SSO institucional das universidades
- Trilha de auditoria detalhada
- Hierarquia abaixo da instituição (departamentos, cursos)
- Compartilhamento parcial de dados entre instituições

## Suposições

| ID | Suposição | Status | Resolução |
|---|---|---|---|
| ASM-004 | O access token JWT tem validade de 1 hora e o refresh token de 7 dias (httpOnly cookie) | confirmada | Definido no TDD — balanceia segurança e UX |
| ASM-005 | Autocadastro de usuários é aberto, mas sem vínculo a instituição o usuário não acessa dados | confirmada | Alinhado com o usuário — gestor convida, vínculo é criado |
| ASM-006 | Um mesmo usuário pode pertencer a múltiplas instituições com papéis diferentes | confirmada | Resposta do usuário — tabela user_institution M:N |
| ASM-007 | ADMIN é modelado como boolean `is_admin` no `app_user`, não como role na `user_institution` | confirmada | Decisão alinhada — ADMIN é global, não escopado a instituição |

## Perguntas em aberto

Nenhuma.
