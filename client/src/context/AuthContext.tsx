import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react'
import type React from 'react'
import { api } from '@/lib/api/client'
import {
  type AuthResponse,
  type LoginPayload,
  type RegisterPayload,
  type UserProfile,
  login as apiLogin,
  refreshToken as apiRefresh,
  register as apiRegister,
} from '@/lib/api/auth'

type AuthState = {
  user: UserProfile | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (payload: LoginPayload) => Promise<void>
  register: (payload: RegisterPayload) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserProfile | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const handleAuthResponse = useCallback((response: AuthResponse) => {
    api.defaults.headers.common.Authorization = `Bearer ${response.accessToken}`
    setUser(response.user)
  }, [])

  const login = useCallback(
    async (payload: LoginPayload) => {
      const response = await apiLogin(payload)
      handleAuthResponse(response)
    },
    [handleAuthResponse],
  )

  const register = useCallback(
    async (payload: RegisterPayload) => {
      const response = await apiRegister(payload)
      handleAuthResponse(response)
    },
    [handleAuthResponse],
  )

  const logout = useCallback(() => {
    delete api.defaults.headers.common.Authorization
    setUser(null)
  }, [])

  useEffect(() => {
    apiRefresh()
      .then(handleAuthResponse)
      .catch(() => {})
      .finally(() => setIsLoading(false))
  }, [handleAuthResponse])

  const value = useMemo(
    () => ({
      user,
      isAuthenticated: user !== null,
      isLoading,
      login,
      register,
      logout,
    }),
    [user, isLoading, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
