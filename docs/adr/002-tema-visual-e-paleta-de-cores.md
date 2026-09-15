# ADR-002: Tema visual e paleta de cores

- **Data**: 2026-09-15
- **Status**: Aceito
- **Decisores**: Andrey Dedey
- **Tags**: design, ui, tema, cores, tailwind

## Contexto e Problema

O projeto adota shadcn/ui como biblioteca de componentes (ADR-001). O shadcn/ui
vem com um tema base neutro (cinza/preto/branco) que não comunica a identidade
do produto. Como a plataforma trata de emissões de carbono e sustentabilidade
ambiental, o tema visual precisa reforçar essa associação sem comprometer a
legibilidade e a acessibilidade dos componentes.

A decisão precisa cobrir: paleta de cores primárias e semânticas, tipografia,
e a estratégia de customização do shadcn/ui via variáveis CSS do Tailwind.

## Fatores de Decisão

- A plataforma é voltada a gestores de laboratório e coordenadores acadêmicos — o tom deve ser profissional e sóbrio, não lúdico
- A temática ambiental sugere verde como cor primária, mas verde saturado demais prejudica contraste em textos e bordas
- O shadcn/ui usa variáveis CSS em HSL que mapeiam para classes do Tailwind; a customização deve respeitar essa convenção para não quebrar componentes
- A tipografia precisa funcionar bem em tabelas densas (muitos dados numéricos) e em títulos de página
- O design já foi prototipado em Pencil (pen.dev) e os tokens de cor estão validados visualmente nas telas

## Decisão

Adotamos uma paleta centrada em **verde florestal dessaturado** (`#24744D`) como
cor primária, com superfícies de fundo levemente esverdeadas para reforçar a
identidade sem competir com o conteúdo.

### Paleta de Cores

#### Cores de superfície

| Token               | Hex       | Uso                                           |
|----------------------|-----------|-----------------------------------------------|
| `bg-page`            | `#FBFCF9` | Fundo geral da aplicação                      |
| `bg-card`            | `#FFFFFF` | Fundo de cards, modais e áreas de conteúdo     |
| `bg-muted`           | `#EEF1EC` | Fundo de cabeçalhos de tabela, áreas inativas  |
| `bg-secondary`       | `#E8EDE8` | Fundo de alertas informativos, badges          |
| `bg-accent-soft`     | `#DEECE2` | Fundo de ícones em destaque, indicadores       |
| `bg-sidebar`         | `#FFFFFF` | Fundo da sidebar de navegação                  |

#### Cores de texto

| Token               | Hex       | Uso                                           |
|----------------------|-----------|-----------------------------------------------|
| `fg-primary`         | `#192219` | Texto principal, títulos, valores              |
| `fg-muted`           | `#6D786D` | Texto secundário, labels, breadcrumbs          |

#### Cores de marca e ação

| Token               | Hex       | Uso                                           |
|----------------------|-----------|-----------------------------------------------|
| `color-primary`      | `#24744D` | Botões primários, links, ícones de ação        |
| `color-primary-fg`   | `#FFFFFF` | Texto sobre fundo primário                     |

#### Cores de borda

| Token               | Hex       | Uso                                           |
|----------------------|-----------|-----------------------------------------------|
| `color-border`       | `#DDE3DD` | Bordas de cards, inputs, divisórias            |
| `sidebar-border`     | `#E1E6E0` | Borda da sidebar                               |

#### Cores semânticas e de gráfico

| Token               | Hex       | Uso                                           |
|----------------------|-----------|-----------------------------------------------|
| `chart-green`        | `#24744D` | Barras e linhas de gráfico — série principal   |
| `chart-amber`        | `#BD7138` | Barras e linhas de gráfico — série secundária  |
| Destrutivo           | `#E7000B` | Ações destrutivas (excluir, erro)              |
| Alerta               | `#F59E0B` | Avisos, estados de atenção                     |
| Sucesso              | `#24744D` | Confirmações, estados positivos                |

### Tipografia

| Uso                | Família   | Peso       | Tamanho |
|--------------------|-----------|------------|---------|
| Títulos de página  | Archivo   | 700 (Bold) | 24px    |
| Subtítulos / Cards | Archivo   | 600 (Semi) | 16–20px |
| Corpo / Tabelas    | Archivo   | 400–500    | 13–14px |
| Labels / Captions  | Archivo   | 500        | 11–12px |
| Breadcrumbs        | Archivo   | 400        | 12px    |

**Archivo** foi escolhida por ser uma fonte sem serifa com boa legibilidade em
tamanhos pequenos (tabelas densas), variação de peso suficiente para hierarquia
visual, e disponibilidade gratuita no Google Fonts.

### Integração com shadcn/ui e Tailwind

Os tokens acima serão mapeados como variáveis CSS no `globals.css` do projeto,
seguindo a convenção do shadcn/ui. O mapeamento para as variáveis padrão do
shadcn é:

```css
:root {
  --background: 100 14% 98%;        /* bg-page #FBFCF9 */
  --foreground: 120 17% 11%;        /* fg-primary #192219 */
  --card: 0 0% 100%;                /* bg-card #FFFFFF */
  --card-foreground: 120 17% 11%;   /* fg-primary */
  --primary: 153 52% 30%;           /* color-primary #24744D */
  --primary-foreground: 0 0% 100%;  /* color-primary-fg #FFFFFF */
  --secondary: 120 10% 92%;         /* bg-secondary #E8EDE8 */
  --secondary-foreground: 120 17% 11%;
  --muted: 110 9% 94%;              /* bg-muted #EEF1EC */
  --muted-foreground: 120 8% 45%;   /* fg-muted #6D786D */
  --accent: 140 16% 90%;            /* bg-accent-soft #DEECE2 */
  --accent-foreground: 120 17% 11%;
  --destructive: 357 100% 45%;      /* #E7000B */
  --border: 120 7% 88%;             /* color-border #DDE3DD */
  --input: 120 7% 88%;
  --ring: 153 52% 30%;              /* color-primary */
  --sidebar-background: 0 0% 100%;
  --sidebar-border: 110 6% 89%;     /* sidebar-border #E1E6E0 */
  --chart-1: 153 52% 30%;           /* chart-green */
  --chart-2: 26 47% 48%;            /* chart-amber */
}
```

### Consequências Positivas

- Identidade visual coerente com a temática ambiental, sem ser caricata
- Contraste suficiente: `fg-primary` (#192219) sobre `bg-page` (#FBFCF9) atinge ratio 15.3:1, muito acima do mínimo WCAG AA (4.5:1)
- Tokens nomeados por função (não por cor) facilitam ajustes futuros sem renomear classes
- Fonte única (Archivo) simplifica carregamento e mantém consistência
- Mapeamento direto para variáveis shadcn/ui — componentes funcionam sem overrides individuais

### Consequências Negativas

- Paleta monocromática (verde) oferece pouca diferenciação para séries de dados com mais de duas categorias; gráficos complexos podem precisar de cores adicionais
- Archivo não está entre as fontes mais populares do ecossistema shadcn/ui (que costuma usar Inter ou Geist); pode causar estranhamento inicial
- Tema apenas light — modo escuro não está contemplado nesta decisão

## Links

- Protótipos visuais: `../../../Designs/TCC_carbon_calculator.pen` (pen.dev)
- [ADR-001 — Stack](./001-stack-spring-boot-react-vite-bun-shadcn-postgres.md)
- [shadcn/ui Theming](https://ui.shadcn.com/docs/theming)
- [Google Fonts — Archivo](https://fonts.google.com/specimen/Archivo)
