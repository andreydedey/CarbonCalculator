import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({
    login: vi.fn(),
    user: null,
    isAuthenticated: false,
    isLoading: false,
    acceptInvite: vi.fn(),
    logout: vi.fn(),
  }),
}))

vi.mock('@/components/auth/AuthBrandPanel', () => ({
  AuthBrandPanel: () => <div data-testid="brand-panel" />,
}))

vi.mock('@/components/auth/GoogleAuthButton', () => ({
  GoogleAuthButton: () => <div data-testid="google-btn" />,
}))

// @spec:AC-168 login page has no register link
describe('LoginPage', () => {
  it('does not show a registration link', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    )

    expect(screen.queryByText(/Criar conta/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/Registrar/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /register/i })).not.toBeInTheDocument()

    expect(screen.getByText(/Solicite acesso a um gestor da sua instituição/)).toBeInTheDocument()
  })
})
