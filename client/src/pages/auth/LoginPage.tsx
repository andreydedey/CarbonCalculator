import { zodResolver } from '@hookform/resolvers/zod'
import type React from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { AuthBrandPanel } from '@/components/auth/AuthBrandPanel'
import { GoogleAuthButton } from '@/components/auth/GoogleAuthButton'
import { Button } from '@/components/ui/button'
import { FieldError } from '@/components/ui/field-error'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { LoginFormSkeleton } from '@/components/ui/skeletons'
import { useAuth } from '@/context/AuthContext'
import { ApiError } from '@/lib/api/client'
import { type LoginFormData, loginSchema } from '@/lib/schemas/authSchemas'

export const LoginPage: React.FC = () => {
  const { login, isLoading: authLoading } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const oauthError = searchParams.get('error')

  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  })

  const onSubmit = async (data: LoginFormData) => {
    clearErrors('root')
    try {
      await login(data)
      navigate('/dashboard')
    } catch (err) {
      setError('root', { message: err instanceof ApiError ? err.message : 'Erro inesperado' })
    }
  }

  if (authLoading) {
    return (
      <div className="flex min-h-svh">
        <AuthBrandPanel />
        <div className="flex w-full flex-col items-center justify-center px-6 lg:w-1/2">
          <LoginFormSkeleton />
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-svh">
      <AuthBrandPanel />

      <div className="flex w-full flex-col items-center justify-center px-6 lg:w-1/2">
        <div className="w-full max-w-sm space-y-6">
          <div className="space-y-2 text-center">
            <h2 className="font-heading text-2xl font-bold">Entrar</h2>
            <p className="text-muted-foreground text-sm">Acesse sua conta para continuar</p>
          </div>

          {oauthError === 'no_invite' && (
            <div className="rounded-md border border-destructive/20 bg-destructive/10 px-4 py-3 text-sm text-destructive">
              Seu e-mail não possui convite. Solicite acesso a um gestor da sua instituição.
            </div>
          )}

          {errors.root && (
            <div className="rounded-md border border-destructive/20 bg-destructive/10 px-4 py-3 text-sm text-destructive">
              {errors.root.message}
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                placeholder="seu.email@universidade.br"
                {...register('email')}
              />
              {errors.email && <FieldError message={errors.email.message} />}
            </div>

            <div className="space-y-2">
              <Label htmlFor="password">Senha</Label>
              <Input
                id="password"
                type="password"
                placeholder="Sua senha"
                {...register('password')}
              />
              {errors.password && <FieldError message={errors.password.message} />}
            </div>

            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? 'Entrando...' : 'Entrar'}
            </Button>
          </form>

          <GoogleAuthButton />

          <p className="text-center text-sm text-muted-foreground">
            Solicite acesso a um gestor da sua instituição.
          </p>
        </div>
      </div>
    </div>
  )
}
