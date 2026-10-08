import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { RegisterPage } from './RegisterPage'

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({
    acceptInvite: vi.fn(),
    user: null,
    isAuthenticated: false,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
  }),
}))

vi.mock('@/components/auth/AuthBrandPanel', () => ({
  AuthBrandPanel: () => <div data-testid="brand-panel" />,
}))

function renderWithProviders(initialEntries: string[]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={initialEntries}>
        <RegisterPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

// @spec:AC-166 page without token shows warning
describe('RegisterPage without token', () => {
  it('shows "Convite necessário" message', () => {
    renderWithProviders(['/register'])

    expect(screen.getByText('Convite necessário')).toBeInTheDocument()
    expect(screen.getByText(/Você precisa de um convite para se registrar/)).toBeInTheDocument()
    expect(screen.getByText('Voltar para login')).toBeInTheDocument()
  })
})

// @spec:AC-165 form displays readonly email
describe('RegisterPage with valid token', () => {
  it('shows readonly email field after token validation', async () => {
    const { validateInvite } = await import('@/lib/api/auth')
    vi.mocked(validateInvite).mockResolvedValue({
      email: 'convidado@uni.br',
      role: 'RESEARCHER',
      institutionName: 'Universidade Teste',
    })

    renderWithProviders(['/register?token=valid-token-123'])

    await waitFor(() => {
      expect(screen.getByLabelText('Email')).toBeInTheDocument()
    })

    const emailInput = screen.getByLabelText('Email') as HTMLInputElement
    expect(emailInput.value).toBe('convidado@uni.br')
    expect(emailInput).toBeDisabled()
  })
})

vi.mock('@/lib/api/auth', () => ({
  validateInvite: vi.fn(),
  login: vi.fn(),
  acceptInvite: vi.fn(),
  refreshToken: vi.fn().mockRejectedValue(new Error('no session')),
}))
