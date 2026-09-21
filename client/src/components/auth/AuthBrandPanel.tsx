import { Leaf } from 'lucide-react'
import type React from 'react'

export const AuthBrandPanel: React.FC = () => (
  <div className="bg-primary hidden flex-col items-center justify-center gap-6 p-10 lg:flex lg:w-1/2">
    <div className="flex flex-col items-center gap-3">
      <Leaf className="size-12 text-white" />
      <h1 className="font-heading text-4xl font-bold text-white">EcoScope</h1>
      <p className="max-w-[360px] text-center text-base leading-relaxed text-white/80">
        Plataforma de Emissões de Carbono{'\n'}do Escopo 2 em Laboratórios Acadêmicos
      </p>
    </div>
  </div>
)
