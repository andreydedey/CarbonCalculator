import type React from 'react'
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import {
  type AcceptInvitePayload,
  type AuthResponse,
  acceptInvite as apiAcceptInvite,
  login as apiLogin,
  refreshToken as apiRefresh,
  type LoginPayload,
  type UserProfile,
} from '@/lib/api/auth'
import { api } from '@/lib/api/client'

type AuthState = {
  user: UserProfile | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (payload: LoginPayload) => Promise<void>
  acceptInvite: (token: string, payload: AcceptInvitePayload) => Promise<void>
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

  const acceptInvite = useCallback(
    async (token: string, payload: AcceptInvitePayload) => {
      const response = await apiAcceptInvite(token, payload)
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
      acceptInvite,
      logout,
    }),
    [user, isLoading, login, acceptInvite, logout],
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
