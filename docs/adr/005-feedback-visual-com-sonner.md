# ADR-005: Feedback visual com Sonner (toast notifications)

- **Data**: 2026-09-20
- **Status**: Aceito
- **Decisores**: Andrey Dedey
- **Tags**: ux, feedback, toast, sonner, shadcn

## Contexto e Problema

Acoes mutativas (criar, editar, desativar, excluir) no sistema concluem sem
nenhum feedback visual ao usuario. O dialog fecha, mas nao ha confirmacao
explicita de que a operacao foi bem-sucedida. Isso prejudica a percepcao de
controle do usuario e dificulta a identificacao de falhas silenciosas.

Precisamos de um padrao leve e consistente de notificacao que:

- Nao interrompa o fluxo do usuario (nao-modal)
- Funcione com o ecossistema ja adotado (Radix + shadcn/ui)
- Seja minimo em configuracao e dependencias

## Fatores de Decisao

- O shadcn/ui ja oferece integracao oficial com Sonner para o ecossistema Radix
- Toasts sao o padrao de feedback mais comum para acoes CRUD em aplicacoes administrativas
- A biblioteca deve respeitar a paleta de cores e tipografia do projeto (ADR-002)

## Opcoes Consideradas

- **A -- Sonner via shadcn/ui**: componente oficial recomendado pelo shadcn para Radix
- **B -- React Hot Toast**: popular, mas sem integracao nativa com shadcn/ui
- **C -- Toast customizado**: implementacao manual sobre Radix Toast primitive

## Decisao

Escolhemos a **Opcao A**, Sonner integrado via shadcn/ui, porque e a solucao
recomendada oficialmente para projetos baseados em Radix e exige configuracao
minima.

### Implementacao

- O componente `<Toaster />` (wrapper do Sonner com tema do projeto) e montado
  uma unica vez no `App.tsx`, posicionado no canto inferior direito
  (`position="bottom-right"`).
- Cada mutation de sucesso dispara `toast.success()` com uma mensagem curta
  descrevendo a acao concluida (ex: "Laboratorio criado.").
- Erros de mutation continuam tratados inline nos formularios/dialogs via
  `FieldError`, sem duplicar em toast -- o toast e reservado para confirmacoes
  de sucesso.

### Convencao de uso

```tsx
import { toast } from 'sonner'

// In mutation onSuccess:
toast.success('Laboratorio criado.')
```

### Consequencias Positivas

- Feedback imediato e nao-intrusivo para toda acao mutativa
- Padrao unico e consistente em todo o sistema
- Zero configuracao alem do `<Toaster />` no root

### Consequencias Negativas

- Dependencia adicional (`sonner`) no bundle do frontend
- Toasts de sucesso podem ser ignorados pelo usuario se a acao ja tiver
  feedback visual obvio (ex: item aparece na lista)
