# ADR-001: Adotar Java Spring Boot, React + Vite com Bun, shadcn/ui e PostgreSQL

- **Data**: 2026-09-13
- **Status**: Aceito
- **Decisores**: Andrey Dedey
- **Tags**: arquitetura, stack, backend, frontend, banco-de-dados

## Contexto e Problema

O CarbonCalculatorTCC é um projeto greenfield: o repositório contém apenas a
estrutura spec-driven (`.spec/`, `.agents/`, `docs/`), sem código de aplicação.
Antes de especificar features, precisamos fixar a stack, porque ela determina
como os critérios de aceite serão verificados (ver [P-001](../../.spec/constituicao.md)).

Sendo um TCC, o prazo é rígido e o desenvolvimento é conduzido por uma equipe
muito pequena. O fator decisivo é **reduzir risco e tempo de aprendizado**: já
existe um sistema em produção do mesmo autor, o
[SaleSheet](https://github.com/andreydedey/SaleSheet), cuja composição resolveu
os mesmos problemas (autenticação, CRUD paginado, dashboard, migrations).
Reaproveitar aquela arquitetura converte decisões em precedentes já validados.

## Fatores de Decisão

- Domínio fortemente relacional (fatores de emissão, cálculos, histórico) com necessidade de integridade transacional
- Familiaridade prévia da equipe — curva de aprendizado próxima de zero é mais valiosa que qualquer ganho marginal de tecnologia
- Necessidade de defender a escolha na banca com um precedente concreto em produção
- Interface administrativa rica (tabelas, formulários, diálogos) sem orçamento para design system próprio
- Deploy simples e barato, sem infraestrutura dedicada

## Opções Consideradas

- **A — Espelhar a stack do SaleSheet**: Spring Boot + React/Vite + shadcn/ui + PostgreSQL
- **B — Stack unificada em TypeScript**: Next.js full-stack com Prisma
- **C — Python**: Django ou FastAPI no back-end, mantendo React no front

## Decisão

Escolhemos a **Opção A**, espelhando a composição do SaleSheet, porque é a única
que elimina simultaneamente o risco de arquitetura e o custo de aprendizado — os
dois maiores inimigos de um prazo de TCC. Concretamente:

- **Back-end**: Java + Spring Boot, organizado em `controllers / services / repositories / entities / dto / mappers`, com Spring Data JPA e Spring Security.
- **Banco**: PostgreSQL, com schema versionado por migrations Flyway em `src/main/resources/db/migration`.
- **Front-end**: React + TypeScript com Vite, em `client/`, seguindo a divisão `pages / components / layout / lib / hooks / context`.
- **Componentes**: shadcn/ui sobre Radix + Tailwind CSS, com os componentes copiados para `components/ui/`.
- **Formulários**: React Hook Form para gerenciamento de estado de formulários, com Zod para definição e validação dos schemas. A validação de entrada acontece primariamente no frontend; o backend valida regras de negócio na camada de serviço, sem uso de Bean Validation (`@Valid`).
- **Lint e formatação**: Biome, substituindo ESLint/Prettier/oxlint como ferramenta unificada de lint e formatação.
- **Runtime/gerenciador do front**: **Bun**, no lugar do npm usado pelo SaleSheet.
- **Layout**: monorepo único, com `client/` e `server/` lado a lado.

### Política de Versões

Fixamos o **maior suporte de longo prazo disponível**, para que o projeto não
exija atualização de plataforma durante nem logo após a defesa:

- **Java 25 (LTS)**, lançado em 2025-09 — sucede o Java 21 usado pelo SaleSheet.
- **PostgreSQL 18**, major estável mais recente, com suporte até 2030-11.
  O PostgreSQL não publica releases "LTS": todo major recebe cerca de cinco anos
  de correções, então "mais novo" e "mais suportado" são a mesma escolha.

A Opção B foi descartada porque um domínio de cálculo com regras de negócio
densas se beneficia de tipagem nominal e da camada de serviço explícita do
Spring; a Opção C, porque trocaria familiaridade por nada que o projeto exija.

Bun é o único desvio deliberado em relação ao precedente: instalação e dev server
sensivelmente mais rápidos, com compatibilidade de `package.json` suficiente para
que o risco seja contornável (voltar ao npm é trivial e não afeta o código).

### Consequências Positivas

- Estrutura de pastas, padrões e decisões de segurança já validados em produção
- Migrations Flyway dão histórico de schema auditável, útil como evidência no TCC
- shadcn/ui entrega uma UI consistente sem manter um design system
- Separação `client`/`server` permite evoluir e implantar as partes de forma independente

### Consequências Negativas

- Duas toolchains (Maven/JVM e Bun/Node) para instalar, versionar e ensinar
- Sem compartilhamento de tipos entre back e front: contratos de DTO precisam ser mantidos manualmente em sincronia
- Bun é menos maduro que o npm; incompatibilidades de pacote podem exigir fallback
- Componentes shadcn/ui vivem no nosso repositório: atualizações upstream não chegam automaticamente
- JVM tem consumo de memória e tempo de partida maiores — relevante em hospedagem gratuita

## Prós e Contras das Opções

### A — Stack do SaleSheet (Spring Boot + React/Vite + PostgreSQL) ✅ Escolhida

- ✅ Precedente em produção: riscos arquiteturais já pagos
- ✅ Camada de serviço e JPA adequados a regras de negócio densas
- ✅ Ecossistema Spring cobre segurança, validação e testes sem bibliotecas de terceiros
- ❌ Duas toolchains e nenhum tipo compartilhado
- ❌ Mais verboso: um recurso novo toca controller, service, repository, DTO e mapper

### B — Next.js full-stack com Prisma

- ✅ Uma linguagem, tipos compartilhados ponta a ponta, menos código repetido
- ❌ Equipe teria de aprender o modelo de servidor do Next durante o prazo do TCC
- ❌ Fronteira cliente/servidor difusa complica a prova mecânica dos critérios de aceite

### C — Django ou FastAPI + React

- ✅ Django Admin resolveria parte do CRUD administrativo de graça
- ❌ Sem familiaridade prévia e sem precedente próprio para defender
- ❌ Mantém as duas toolchains sem eliminar nenhuma desvantagem da Opção A

## Links

- Repositório de referência: [andreydedey/SaleSheet](https://github.com/andreydedey/SaleSheet)
- [Constituição do projeto](../../.spec/constituicao.md) — P-001 exige prova executável para todo requisito
