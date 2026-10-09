# Spec: Registro por convite

> feature: registro-por-convite
> status: auditada

## Contexto

O sistema permite que qualquer pessoa se registre em `/register`, criando contas
sem vínculo com instituição. Como o acesso é baseado em convite do gestor, o
registro aberto não faz sentido — gera contas órfãs e superfície de ataque
desnecessária. Esta feature fecha o registro: só quem tem um link de convite
com token criptográfico pode criar conta.

## Histórias

### US-047 — Convite gera link com token criptográfico

Como gestor, quero que ao convidar um membro o sistema gere um link com
token seguro, para que eu possa copiar e enviar ao convidado por qualquer canal.

#### AC-155 — Resposta do convite inclui link

- **Dado** um gestor autenticado na tela de Gestão de Usuários
- **Quando** ele convida um email que ainda não tem conta
- **Então** a resposta inclui um campo `inviteLink` com o token (ex: `/register?token=abc...`)

#### AC-156 — Token armazenado como hash

- **Dado** um convite recém-criado no banco
- **Quando** inspecionamos a coluna `invite_token_hash` de `user_institution`
- **Então** o valor é o SHA-256 do token bruto (o token bruto não é persistido)

#### AC-157 — Token expira em 7 dias

- **Dado** um convite criado há mais de 7 dias
- **Quando** o convidado tenta validar o token
- **Então** o sistema rejeita com erro informando que o convite expirou (HTTP 400)

#### AC-158 — Convite de usuário já registrado não gera link

- **Dado** um usuário já registrado no sistema
- **Quando** o gestor convida o email desse usuário
- **Então** o vínculo é criado como ACTIVE imediatamente e `inviteLink` é `null`

### US-048 — Registro exclusivo por token de convite

Como convidado, quero acessar o link de convite e preencher meu nome e senha,
para que minha conta seja criada e vinculada à instituição automaticamente.

#### AC-159 — Validação de token retorna dados do convite

- **Dado** um token de convite válido e não expirado
- **Quando** o frontend chama o endpoint de validação
- **Então** a resposta contém o email, papel e nome da instituição (HTTP 200)

#### AC-160 — Token inválido é rejeitado na validação

- **Dado** um token inexistente ou adulterado
- **Quando** o frontend chama o endpoint de validação
- **Então** o sistema retorna erro (HTTP 400) com mensagem "Convite inválido ou expirado"

#### AC-161 — Aceitar convite cria conta e ativa vínculo

- **Dado** um token válido e dados de registro (nome e senha)
- **Quando** o convidado submete o formulário de registro
- **Então** a conta é criada, o vínculo passa para ACTIVE, e o sistema retorna JWT com login automático (HTTP 201)

#### AC-162 — Token consumido não pode ser reutilizado

- **Dado** um token que já foi usado para criar uma conta
- **Quando** alguém tenta usar o mesmo token novamente
- **Então** o sistema rejeita com erro (HTTP 400)

#### AC-163 — Aceitar convite ativa outros convites PENDING do mesmo email

- **Dado** um email com convites pendentes em mais de uma instituição
- **Quando** o convidado aceita o convite de uma delas
- **Então** todos os convites pendentes para aquele email são ativados automaticamente

#### AC-164 — Rejeitar aceitação se email já registrado

- **Dado** um token válido cujo email já tem conta no sistema
- **Quando** alguém tenta aceitar o convite
- **Então** o sistema retorna erro (HTTP 409) informando que o email já está registrado

#### AC-165 — Formulário de registro exibe email readonly

- **Dado** que o convidado acessou `/register?token=<token_valido>`
- **Quando** a página carrega e o token é validado
- **Então** o campo de email aparece preenchido e bloqueado (readonly), vindo do convite

#### AC-166 — Página de registro sem token mostra aviso

- **Dado** que alguém acessa `/register` sem query param `token`
- **Quando** a página carrega
- **Então** exibe mensagem "Você precisa de um convite para se registrar" e link para `/login`

### US-049 — Registro aberto removido

Como administrador do sistema, quero que o endpoint público de registro seja
removido, para que nenhuma conta seja criada sem convite.

#### AC-167 — Endpoint POST /auth/register removido

- **Dado** o endpoint antigo `POST /auth/register`
- **Quando** alguém faz uma requisição para ele
- **Então** recebe HTTP 404

#### AC-168 — Página de login sem link de registro

- **Dado** a página de login (`/login`)
- **Quando** o usuário visualiza a página
- **Então** não há link para "Criar conta" / "Registrar-se"; em vez disso, há texto "Solicite acesso a um gestor"

### US-050 — UI de link de convite para o gestor

Como gestor, quero que após convidar alguém a interface me mostre o link
de convite para eu copiar, para que eu envie ao convidado pelo canal que preferir.

#### AC-169 — Dialog exibe link copiável após convite

- **Dado** que o gestor acabou de convidar um email sem conta
- **Quando** o convite é criado com sucesso
- **Então** a UI exibe um dialog com o link completo e um botão "Copiar" que copia para o clipboard

#### AC-170 — Botão copiar funciona

- **Dado** o dialog com o link de convite aberto
- **Quando** o gestor clica no botão "Copiar"
- **Então** o link é copiado para o clipboard e o botão indica sucesso (ex: texto muda para "Copiado!")

## Fora de escopo

- Envio automático de email (SMTP/SendGrid) — o gestor copia o link manualmente
- Reenvio de convite expirado pela UI (gestor pode revogar e convidar novamente)
- Rate limiting nos endpoints de convite
- Recuperação de senha
- Google OAuth com validação de convite (será tratado em feature separada quando OAuth for implementado)

## Suposições

| ID | Suposição | Status | Resolução |
|---|---|---|---|
| ASM-035 | O app roda sempre em HTTPS em produção, protegendo o token na URL | confirmada | Padrão da indústria; token é de uso único mesmo se interceptado |
| ASM-036 | 7 dias é tempo suficiente para o convidado usar o link | confirmada | Padrão usado por Slack, Linear e outros SaaS |
| ASM-037 | Não há convites PENDING sem email no banco atual | confirmada | Verificado: todo convite PENDING tem `user_email` preenchido |

## Perguntas em aberto

Nenhuma.
