import type React from 'react'
import { Route, Routes, useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Toaster } from '@/components/ui/sonner'
import { InstitutionProvider } from '@/context/InstitutionContext'
import { InstitutionsPage } from '@/pages/institutions/InstitutionsPage'
import { LaboratoriesPage } from '@/pages/laboratories/LaboratoriesPage'

const LandingPage: React.FC = () => {
  const navigate = useNavigate()

  return (
    <main className="flex min-h-svh flex-col items-center justify-center gap-4">
      <h1 className="font-heading text-2xl font-bold">Carbon Calculator</h1>
      <p className="text-muted-foreground text-sm">Esqueleto do projeto.</p>
      <Button onClick={() => navigate('/institutions')}>Começar</Button>
    </main>
  )
}

export const App: React.FC = () => (
  <InstitutionProvider>
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/institutions" element={<InstitutionsPage />} />
      <Route path="/laboratories" element={<LaboratoriesPage />} />
    </Routes>
    <Toaster position="bottom-right" />
  </InstitutionProvider>
)
