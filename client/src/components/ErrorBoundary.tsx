import { Component, type ErrorInfo, lazy, type ReactNode, Suspense } from 'react'

const ErrorPage = lazy(() => import('@/pages/ErrorPage'))

interface Props {
  children: ReactNode
  fallback?: ReactNode
}

interface State {
  hasError: boolean
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('React error boundary caught error:', error.message, info.componentStack)
  }

  render() {
    if (this.state.hasError) {
      return (
        this.props.fallback ?? (
          <Suspense
            fallback={
              <div className="flex min-h-screen items-center justify-center bg-background">
                <div className="text-center">
                  <h1 className="text-xl font-semibold text-foreground">Algo deu errado</h1>
                  <p className="mt-2 text-sm text-muted-foreground">
                    Recarregue a página para tentar novamente.
                  </p>
                </div>
              </div>
            }
          >
            <ErrorPage />
          </Suspense>
        )
      )
    }

    return this.props.children
  }
}
