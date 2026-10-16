import type React from 'react'
import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'

// The OAuth redirect only sets the refresh cookie; AuthProvider exchanges it for a session on load
export const OAuthCallbackPage: React.FC = () => {
  const navigate = useNavigate()
  const { isAuthenticated, isLoading } = useAuth()

  useEffect(() => {
    if (isLoading) return
    navigate(isAuthenticated ? '/dashboard' : '/login', { replace: true })
  }, [isAuthenticated, isLoading, navigate])

  return (
    <div className="flex h-screen items-center justify-center">
      <div className="text-center">
        <div className="mx-auto mb-4 h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        <p className="text-muted-foreground">Autenticando...</p>
      </div>
    </div>
  )
}
