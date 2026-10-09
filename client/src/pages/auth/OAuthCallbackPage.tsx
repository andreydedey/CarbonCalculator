import type React from 'react'
import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { api } from '@/lib/api/client'
import { getProfile } from '@/lib/api/auth'

export const OAuthCallbackPage: React.FC = () => {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { setUserFromOAuth } = useAuth()

  useEffect(() => {
    const token = searchParams.get('token')
    if (!token) {
      navigate('/login', { replace: true })
      return
    }

    api.defaults.headers.common.Authorization = `Bearer ${token}`
    getProfile()
      .then((user) => {
        setUserFromOAuth(user)
        navigate('/dashboard', { replace: true })
      })
      .catch(() => {
        delete api.defaults.headers.common.Authorization
        navigate('/login', { replace: true })
      })
  }, [searchParams, navigate, setUserFromOAuth])

  return (
    <div className="flex h-screen items-center justify-center">
      <div className="text-center">
        <div className="mx-auto mb-4 h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        <p className="text-muted-foreground">Autenticando...</p>
      </div>
    </div>
  )
}
