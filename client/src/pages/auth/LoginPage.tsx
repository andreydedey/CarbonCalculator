import { zodResolver } from '@hookform/resolvers/zod'
import type React from 'react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { AuthBrandPanel } from '@/components/auth/AuthBrandPanel'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { FieldError } from '@/components/ui/field-error'
import { useAuth } from '@/context/AuthContext'
import { type LoginFormData, loginSchema } from '@/lib/schemas/authSchemas'
import { ApiError } from '@/lib/api/client'

export const LoginPage: React.FC = () => {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  })

  const onSubmit = async (data: LoginFormData) => {
    setError(null)
    try {
      await login(data)
      navigate('/laboratories')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Erro inesperado')
    }
  }

  return (
    <div className="flex min-h-svh">
      <AuthBrandPanel />

      <div className="flex w-full flex-col items-center justify-center px-6 lg:w-1/2">
        <div className="w-full max-w-sm space-y-6">
          <div className="space-y-2 text-center">
            <h2 className="font-heading text-2xl font-bold">Entrar</h2>
            <p className="text-muted-foreground text-sm">
              Acesse sua conta para continuar
            </p>
          </div>

          {error && (
            <div className="rounded-md border border-destructive/20 bg-destructive/10 px-4 py-3 text-sm text-destructive">
              {error}
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

          <p className="text-center text-sm text-muted-foreground">
            Não tem uma conta?{' '}
            <Link to="/register" className="text-primary hover:underline">
              Criar conta
            </Link>
          </p>
          <p className="text-center text-xs text-muted-foreground">
            Solicite ao gestor da sua instituição acesso ao sistema.
          </p>
        </div>
      </div>
    </div>
  )
}
