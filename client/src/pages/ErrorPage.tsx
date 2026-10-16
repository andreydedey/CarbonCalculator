import { Bug, Home, RefreshCw } from 'lucide-react'
import { useCallback } from 'react'
import { Button } from '@/components/ui/button'

export default function ErrorPage() {
  const handleReload = useCallback(() => {
    window.location.reload()
  }, [])

  const handleGoHome = useCallback(() => {
    window.location.href = '/dashboard'
  }, [])

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4">
      <div className="flex max-w-md flex-col items-center text-center">
        {/* Wilting plant animation */}
        <div className="relative mb-8" aria-hidden>
          {/* Pot */}
          <div className="relative mx-auto w-20">
            {/* Stem */}
            <div className="mx-auto h-16 w-1.5 origin-bottom animate-[wilt_3s_ease-in-out_infinite] rounded-full bg-primary/40" />
            {/* Leaves drooping */}
            <div className="absolute top-0 left-1/2 -translate-x-1/2">
              <div className="absolute -left-3 top-0 h-4 w-6 origin-right animate-[droop_3s_ease-in-out_infinite] rounded-full bg-primary/25 -rotate-12" />
              <div className="absolute -right-3 top-0 h-4 w-6 origin-left animate-[droop_3s_ease-in-out_infinite_0.3s] rounded-full bg-primary/30 rotate-12" />
              <div className="absolute -left-1 -top-2 h-3 w-5 origin-right animate-[droop_3s_ease-in-out_infinite_0.6s] rounded-full bg-primary/20 -rotate-25" />
            </div>
            {/* Pot body */}
            <div className="mx-auto h-3 w-16 rounded-t-sm bg-chart-2/30" />
            <div
              className="mx-auto h-10 w-14 rounded-b-lg bg-chart-2/25"
              style={{ clipPath: 'polygon(8% 0%, 92% 0%, 80% 100%, 20% 100%)' }}
            />
          </div>
          {/* Sad water drops */}
          <div className="absolute -right-2 top-6 animate-[drip_2s_ease-in_infinite] text-xs text-primary/20">
            💧
          </div>
        </div>

        {/* Error icon badge */}
        <div className="mb-4 flex size-12 items-center justify-center rounded-full bg-destructive/10">
          <Bug className="size-6 text-destructive" />
        </div>

        <h1 className="mb-2 font-heading text-2xl font-bold text-foreground">Algo deu errado</h1>

        <p className="mb-2 text-muted-foreground">Nossa aplicação encontrou um erro inesperado.</p>
        <p className="mb-8 text-sm text-muted-foreground">
          Assim como uma planta precisa de cuidado, estamos trabalhando para resolver. Tente
          recarregar a página.
        </p>

        <div className="flex flex-wrap items-center justify-center gap-3">
          <Button variant="outline" onClick={handleGoHome}>
            <Home data-icon="inline-start" />
            Ir para o início
          </Button>
          <Button onClick={handleReload}>
            <RefreshCw data-icon="inline-start" />
            Recarregar página
          </Button>
        </div>
      </div>

      <style>{`
        @keyframes wilt {
          0%, 100% { transform: rotate(0deg); }
          50% { transform: rotate(8deg); }
        }
        @keyframes droop {
          0%, 100% { transform: rotate(var(--tw-rotate)) translateY(0); }
          50% { transform: rotate(calc(var(--tw-rotate) - 15deg)) translateY(3px); }
        }
        @keyframes drip {
          0% { transform: translateY(0); opacity: 0.6; }
          80% { opacity: 0.3; }
          100% { transform: translateY(20px); opacity: 0; }
        }
      `}</style>
    </div>
  )
}
