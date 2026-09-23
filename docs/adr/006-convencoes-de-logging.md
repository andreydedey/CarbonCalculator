# ADR-006: Convenções de logging

- **Data**: 2026-09-23
- **Status**: Aceito
- **Decisores**: Andrey Dedey
- **Tags**: observabilidade, logging, backend, frontend, segurança

## Contexto e Problema

O projeto não possui uma estratégia de logging definida. Atualmente, o único log
explícito é o `log.error` no `GlobalExceptionHandler` para erros 500 inesperados.
Isso dificulta o diagnóstico de bugs em produção — como o que encontramos recentemente
onde o header `X-Institution-Id` não era enviado pelo frontend e a causa raiz só foi
descoberta por leitura manual do código.

Precisamos definir:

- **O que** logar (e o que nunca logar)
- **Quando** usar cada nível de log
- **Como** formatar e estruturar as entradas
- **Onde** injetar contexto para rastreabilidade (correlation IDs, tenant, usuário)

O objetivo é detectar bugs mais cedo, reduzir o tempo de diagnóstico e manter a
segurança dos dados.

## Fatores de Decisão

- O projeto usa Spring Boot 4.1 (ADR-001), que suporta structured logging nativo
  desde a versão 3.4 — sem dependências extras
- Multi-tenancy via RLS (ADR-004) exige que o `institutionId` apareça nos logs para
  filtrar por tenant
- O volume de tráfego é baixo (projeto acadêmico), então overhead de logging é
  desprezível
- A equipe é pequena — logs claros compensam a falta de ferramentas de APM dedicadas
- OWASP Top 10:2025 inclui "Security Logging and Alerting Failures" (A09)

## Decisão

Adotamos as convenções abaixo, organizadas em backend e frontend.

---

### 1. Níveis de log — quando usar cada um

| Nível   | Quando usar                                                        | Produção? |
|---------|--------------------------------------------------------------------|-----------|
| `TRACE` | Caminho de execução detalhado (entry/exit de método, loops)        | Nunca     |
| `DEBUG` | Informação diagnóstica: queries, payloads sanitizados, variáveis   | Temporariamente, via Actuator |
| `INFO`  | Eventos de negócio significativos: startup, operações CRUD, login  | Sim — nível padrão |
| `WARN`  | Situações inesperadas mas recuperáveis: retry, threshold próximo   | Sim       |
| `ERROR` | Falha em operação/request, após retries esgotados                  | Sim — deve gerar alerta |

**Regra:** se o sistema tratou a condição normalmente, não é ERROR — é WARN ou INFO.

### 2. Structured logging (JSON em produção)

Spring Boot 4.1 suporta structured logging nativo. A configuração é por profile:

- **Desenvolvimento** (`dev`): texto legível com cores (padrão do Spring Boot)
- **Produção** (`prod`): JSON estruturado via ECS (Elastic Common Schema)

```yaml
# application-prod.yaml
logging:
  structured:
    format:
      console: ecs
    ecs:
      service:
        name: carboncalculator
        version: ${app.version:0.0.1}
        environment: production
```

Não é necessário adicionar `logstash-logback-encoder` nem `logback-spring.xml` — a
configuração nativa do Spring Boot é suficiente.

### 3. MDC — contexto por request

Usar SLF4J MDC (Mapped Diagnostic Context) para injetar campos automaticamente em
todo log de um request. O `TenantFilter` já é o ponto natural para isso:

| Campo MDC       | Origem                              | Exemplo                                  |
|-----------------|-------------------------------------|------------------------------------------|
| `traceId`       | Gerado no filter (`UUID.randomUUID`) | `a1b2c3d4-e5f6-7890-abcd-ef1234567890`  |
| `institutionId` | Header `X-Institution-Id`           | `b02c515d-9c27-4318-9ae1-027019c876bc`  |
| `userId`        | `SecurityContext` → `AppUser.id`    | `f47ac10b-58cc-4372-a567-0e02b2c3d479`  |

Esses campos aparecem automaticamente em toda entrada de log do request, sem precisar
passá-los manualmente para cada `logger.info(...)`.

**Importante:** sempre limpar o MDC no bloco `finally` para evitar vazamento de
contexto entre requests em threads reutilizadas do pool.

```java
try {
    MDC.put("traceId", UUID.randomUUID().toString());
    MDC.put("institutionId", institutionId);
    MDC.put("userId", user.getId().toString());
    // ... filterChain.doFilter(...)
} finally {
    MDC.clear();
}
```

### 4. Correlation ID frontend-backend

Para rastrear um request do browser até o backend:

1. O axios interceptor gera um UUID via `crypto.randomUUID()` e envia como header
   `X-Correlation-ID`
2. O filter do backend extrai o header e coloca no MDC como `traceId` (em vez de gerar
   um novo)
3. O backend ecoa o header na resposta
4. O frontend loga o mesmo ID em caso de erro, permitindo correlação nos logs

### 5. O que logar no backend

**Logar (INFO):**
- Startup e shutdown da aplicação (Spring Boot já faz)
- Operações de escrita: criação, atualização e exclusão de entidades
- Autenticação: login bem-sucedido, falha de login, logout
- Mudanças de papel/membership em instituições
- Migração de banco executada (Flyway já faz)

**Logar (WARN):**
- Tentativa de acesso sem header `X-Institution-Id`
- Tentativa de acesso a instituição sem permissão
- Token JWT expirado ou inválido
- Validação de negócio que rejeitou uma operação

**Logar (ERROR):**
- Exceções não esperadas (o `GlobalExceptionHandler` já faz isso)
- Falha de conexão com banco ou serviço externo
- Estado inconsistente detectado

**Usar parameterized messages (nunca concatenação):**
```java
// Correto
logger.info("Equipment model created: id={}", model.getId());

// Errado — concatenação avaliada mesmo com nível desabilitado
logger.info("Equipment model created: id=" + model.getId());
```

### 6. O que logar no frontend

O frontend opera no browser do usuário — `console.log` não tem valor em produção.

**Fase atual (TCC):** usar `console.error` para erros de API e error boundaries,
incluindo o correlation ID. Não implementar serviço externo de coleta (Sentry, etc.)
agora — o custo não se justifica para o escopo acadêmico.

**Error boundaries:** os componentes React devem capturar erros de renderização com
error boundaries e logar o `componentStack` para diagnóstico.

**Axios error interceptor:** já existe e converte erros em `ApiError`. Adicionar log
com correlation ID para facilitar a busca nos logs do backend:

```typescript
console.error(`[${correlationId}] API error ${status} on ${method} ${url}: ${message}`)
```

### 7. O que NUNCA logar

Seguindo OWASP Logging Cheat Sheet:

- Senhas e credenciais de autenticação
- Tokens JWT, access tokens, API keys
- Dados pessoais sensíveis: CPF, dados de saúde
- Connection strings com credenciais
- Chaves de criptografia e secrets
- Request/response bodies completos (podem conter dados sensíveis)

**Emails e nomes:** logar apenas quando essencial para diagnóstico (ex: falha de login),
nunca em nível INFO rotineiro. Preferir IDs opacos (userId, institutionId).

### 8. Anti-patterns a evitar

1. **Log-and-throw:** ou loga o erro, ou relança — nunca os dois (duplica a entrada
   nos logs em cada camada)
2. **Perder stack trace:** a exceção deve ser o último argumento do SLF4J, sem
   placeholder `{}`
   ```java
   // Correto — stack trace preservado
   logger.error("Failed to process order {}", orderId, exception);
   // Errado — perde o stack trace
   logger.error("Failed: {}", exception);
   ```
3. **Log em loops fechados:** logar contagens agregadas, não cada iteração
4. **ERROR para condições esperadas:** validação rejeitada é WARN, não ERROR
5. **Swallowing exceptions:** `catch` vazio que nem loga nem relança — o erro
   desaparece silenciosamente
6. **Esquecer MDC.clear():** vaza contexto de um request para o próximo na mesma thread

### 9. Performance

- **Async appender:** não é necessário agora (volume baixo), mas deve ser adicionado
  antes de ir para produção com carga real
- **Nível padrão:** INFO em produção; DEBUG/TRACE habilitados temporariamente via
  Spring Boot Actuator (`POST /actuator/loggers/{name}`) sem restart
- **Rotation:** configurar `maxHistory=30`, `totalSizeCap=1GB`, `maxFileSize=10MB`
  quando houver deploy em servidor com disco

## Consequências Positivas

- Bugs como o do `X-Institution-Id` teriam sido detectados imediatamente: um WARN no
  `TenantFilter` mostraria a ausência do header
- Correlation ID permite rastrear um erro do browser até a linha exata do backend
- MDC com `institutionId` permite filtrar logs por tenant sem esforço manual
- Structured logging (JSON) torna os logs indexáveis por qualquer ferramenta de
  agregação (ELK, Loki, CloudWatch) sem parsing customizado
- Convenções claras evitam logs inconsistentes à medida que o projeto cresce

## Consequências Negativas

- Adicionar logs em todos os pontos definidos requer tocar vários services e filters
- MDC usa ThreadLocal, que não funciona com código reativo (não é o caso deste projeto,
  que usa Spring WebMVC)
- JSON em produção é ilegível para humanos — exige ferramenta ou `jq` para inspeção
  manual

## Links

- [ADR-001 — Stack](./001-stack-spring-boot-react-vite-bun-shadcn-postgres.md)
- [ADR-004 — Multi-tenancy RLS](./004-multitenancy-rls-banco-compartilhado.md)
- [Spring Boot Structured Logging](https://docs.spring.io/spring-boot/reference/features/logging.html#features.logging.structured)
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
- [OWASP A09:2025 — Security Logging Failures](https://owasp.org/Top10/2025/A09_2025-Security_Logging_and_Alerting_Failures/)
- [SLF4J MDC](https://www.slf4j.org/manual.html#mdc)
