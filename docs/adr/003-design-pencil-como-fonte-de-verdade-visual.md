# ADR-003: Design em Pencil como fonte de verdade visual

- **Data**: 2026-09-18
- **Status**: Aceito
- **Decisores**: Andrey Dedey
- **Tags**: design, frontend, ui, processo

## Contexto e Problema

O projeto possui um protótipo visual completo criado no Pencil (pen.dev), localizado
em `design/TCC_carbon_calculator.pen`. Esse arquivo contém as telas, componentes,
espaçamentos e estados visuais validados para toda a plataforma. A paleta de cores
e a tipografia já foram formalizadas no ADR-002.

Sem uma regra explícita, a implementação do frontend pode divergir do design por
decisões ad hoc — escolhas de layout, espaçamento, hierarquia de informação ou
posicionamento de componentes que parecem razoáveis no código mas não correspondem
ao que foi validado visualmente.

## Fatores de Decisão

- O design já foi prototipado e validado antes da implementação começar
- O shadcn/ui oferece componentes genéricos que precisam de orientação visual para serem compostos corretamente
- O projeto é desenvolvido por uma equipe pequena onde o mesmo autor fez o design e fará o código — a tentação de "improvisar" é alta justamente porque o conhecimento está na cabeça e não no processo

## Decisão

O arquivo `design/TCC_carbon_calculator.pen` é a **fonte de verdade visual** do
projeto. Toda implementação de frontend **deve consultar o design antes de
construir uma tela, componente ou fluxo**.

### Na prática

- Antes de implementar uma página ou componente, ler o design correspondente no
  arquivo `.pen` para entender layout, hierarquia, espaçamento e estados visuais
- Quando o design não cobrir um caso específico (estado vazio, erro, loading),
  seguir os padrões visuais já estabelecidos nas telas existentes
- Se uma decisão de implementação conflitar com o design, o design prevalece —
  a menos que haja uma razão técnica documentada para divergir
- Divergências intencionais devem ser registradas como comentário no código
  explicando o motivo

### Consequências Positivas

- Consistência visual garantida entre protótipo e produto final
- Reduz retrabalho por implementações que "parecem certas" mas não seguem o design
- Facilita a defesa do TCC — o avaliador pode comparar protótipo e sistema real

### Consequências Negativas

- Adiciona um passo ao fluxo de desenvolvimento (consultar o .pen antes de codar)
- Arquivos .pen são acessíveis apenas via ferramentas Pencil/MCP — não são legíveis como texto

## Links

- Arquivo de design: `design/TCC_carbon_calculator.pen`
- [ADR-002 — Tema visual e paleta de cores](./002-tema-visual-e-paleta-de-cores.md)
- [ADR-001 — Stack](./001-stack-spring-boot-react-vite-bun-shadcn-postgres.md)
