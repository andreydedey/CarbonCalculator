import { ArrowLeft, Home, Search } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'

const LEAVES = [
  { left: '15%', duration: '6s', delay: '0s', size: '18px' },
  { left: '29%', duration: '7.5s', delay: '0.8s', size: '22px' },
  { left: '43%', duration: '9s', delay: '1.6s', size: '26px' },
  { left: '57%', duration: '10.5s', delay: '2.4s', size: '30px' },
  { left: '71%', duration: '12s', delay: '3.2s', size: '34px' },
  { left: '85%', duration: '13.5s', delay: '4s', size: '38px' },
]

export default function NotFoundPage() {
  const navigate = useNavigate()

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4">
      {/* Floating leaves animation */}
      <div className="pointer-events-none fixed inset-0 overflow-hidden" aria-hidden>
        {LEAVES.map((leaf) => (
          <div
            key={leaf.left}
            className="absolute animate-[fall_linear_infinite] text-primary/20"
            style={{
              left: leaf.left,
              animationDuration: leaf.duration,
              animationDelay: leaf.delay,
              fontSize: leaf.size,
            }}
          >
            🍃
          </div>
        ))}
      </div>

      <div className="relative z-10 flex max-w-md flex-col items-center text-center">
        {/* Big 404 with overlay */}
        <div className="relative mb-6">
          <span className="font-heading text-[8rem] leading-none font-black tracking-tighter text-primary/10">
            404
          </span>
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="flex items-center gap-2 text-primary">
              <Search className="size-5" />
              <span className="font-heading text-lg font-semibold">Caminho não encontrado</span>
            </div>
          </div>
        </div>

        {/* Stump illustration (CSS art) */}
        <div className="mb-8 flex flex-col items-center" aria-hidden>
          <div className="h-8 w-12 rounded-t-full bg-primary/15" />
          <div className="h-3 w-16 rounded-b-sm bg-primary/10" />
          <div className="mt-1 flex gap-3">
            <div className="h-1.5 w-6 -rotate-12 rounded-full bg-primary/8" />
            <div className="h-1.5 w-5 rotate-12 rounded-full bg-primary/8" />
          </div>
        </div>

        <p className="mb-2 text-lg text-foreground">Parece que essa rota foi desmatada.</p>
        <p className="mb-8 text-sm text-muted-foreground">
          A página que você procura não existe ou foi movida para outro lugar.
        </p>

        <div className="flex flex-wrap items-center justify-center gap-3">
          <Button variant="outline" onClick={() => navigate(-1)}>
            <ArrowLeft data-icon="inline-start" />
            Voltar
          </Button>
          <Button asChild>
            <Link to="/dashboard">
              <Home data-icon="inline-start" />
              Ir para o início
            </Link>
          </Button>
        </div>
      </div>

      <style>{`
        @keyframes fall {
          0% {
            transform: translateY(-40px) rotate(0deg);
            opacity: 0;
          }
          10% {
            opacity: 1;
          }
          90% {
            opacity: 1;
          }
          100% {
            transform: translateY(100vh) rotate(360deg);
            opacity: 0;
          }
        }
      `}</style>
    </div>
  )
}
