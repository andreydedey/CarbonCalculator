import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import type React from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { AuthBrandPanel } from '@/components/auth/AuthBrandPanel'
import { Button } from '@/components/ui/button'
import { FieldError } from '@/components/ui/field-error'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/context/AuthContext'
import { validateInvite } from '@/lib/api/auth'
import { ApiError } from '@/lib/api/client'
import { type AcceptInviteFormData, acceptInviteSchema } from '@/lib/schemas/authSchemas'

export const RegisterPage: React.FC = () => {
  const { acceptInvite } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')

  const {
    data: invite,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['invite-validation', token],
    queryFn: () => validateInvite(token!),
    enabled: !!token,
    retry: false,
  })

  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    formState: { errors, isSubmitting },
  } = useForm<AcceptInviteFormData>({
    resolver: zodResolver(acceptInviteSchema),
  })

  const onSubmit = async (data: AcceptInviteFormData) => {
    if (!token) return
    clearErrors('root')
    try {
      await acceptInvite(token, { name: data.name, password: data.password })
      navigate('/dashboard')
    } catch (err) {
      setError('root', { message: err instanceof ApiError ? err.message : 'Erro inesperado' })
    }
  }

  // No token provided
  if (!token) {
    return (
      <div className="flex min-h-svh">
        <AuthBrandPanel />
        <div className="flex w-full flex-col items-center justify-center px-6 lg:w-1/2">
          <div className="w-full max-w-sm space-y-6 text-center">
            <h2 className="font-heading text-2xl font-bold">Convite necessário</h2>
            <p className="text-muted-foreground text-sm">
              Você precisa de um convite para se registrar. Solicite acesso a um gestor da sua
              instituição.
            </p>
            <Link to="/login">
              <Button variant="outline" className="mt-4">
                Voltar para login
              </Button>
            </Link>
          </div>
        </div>
      </div>
    )
  }

  // Loading validation
  if (isLoading) {
    return (
      <div className="flex min-h-svh">
        <AuthBrandPanel />
        <div className="flex w-full flex-col items-center justify-center px-6 lg:w-1/2">
          <p className="text-sm text-muted-foreground">Validando convite...</p>
        </div>
      </div>
    )
  }

  // Invalid or expired token
  if (isError || !invite) {
    return (
      <div className="flex min-h-svh">
        <AuthBrandPanel />
        <div className="flex w-full flex-col items-center justify-center px-6 lg:w-1/2">
          <div className="w-full max-w-sm space-y-6 text-center">
            <h2 className="font-heading text-2xl font-bold">Convite inválido</h2>
            <p className="text-muted-foreground text-sm">
              Este convite é inválido ou expirou. Solicite um novo convite ao gestor da sua
              instituição.
            </p>
            <Link to="/login">
              <Button variant="outline" className="mt-4">
                Voltar para login
              </Button>
            </Link>
          </div>
        </div>
      </div>
    )
  }

  // Valid token — show registration form
  return (
    <div className="flex min-h-svh">
      <AuthBrandPanel />

      <div className="flex w-full flex-col items-center justify-center px-6 lg:w-1/2">
        <div className="w-full max-w-sm space-y-6">
          <div className="space-y-2 text-center">
            <h2 className="font-heading text-2xl font-bold">Criar Conta</h2>
            <p className="text-muted-foreground text-sm">
              Você foi convidado para <span className="font-medium">{invite.institutionName}</span>
            </p>
          </div>

          {errors.root && (
            <div className="rounded-md border border-destructive/20 bg-destructive/10 px-4 py-3 text-sm text-destructive">
              {errors.root.message}
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" value={invite.email} disabled className="opacity-70" />
            </div>

            <div className="space-y-2">
              <Label htmlFor="name">Nome Completo</Label>
              <Input id="name" placeholder="Seu nome completo" {...register('name')} />
              {errors.name && <FieldError message={errors.name.message} />}
            </div>

            <div className="space-y-2">
              <Label htmlFor="password">Senha</Label>
              <Input
                id="password"
                type="password"
                placeholder="Mínimo 6 caracteres"
                {...register('password')}
              />
              {errors.password && <FieldError message={errors.password.message} />}
            </div>

            <div className="space-y-2">
              <Label htmlFor="confirmPassword">Confirmar Senha</Label>
              <Input
                id="confirmPassword"
                type="password"
                placeholder="Repita a senha"
                {...register('confirmPassword')}
              />
              {errors.confirmPassword && <FieldError message={errors.confirmPassword.message} />}
            </div>

            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? 'Criando conta...' : 'Criar Conta'}
            </Button>
          </form>

          <p className="text-center text-sm text-muted-foreground">
            Já tem uma conta?{' '}
            <Link to="/login" className="text-primary hover:underline">
              Entrar
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
