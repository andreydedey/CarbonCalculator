import type React from 'react'
import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from '@/components/auth/ProtectedRoute'
import { ErrorBoundary } from '@/components/ErrorBoundary'
import { AppLayout } from '@/components/layout/AppLayout'
import { Toaster } from '@/components/ui/sonner'
import { AuthProvider } from '@/context/AuthContext'
import { InstitutionProvider } from '@/context/InstitutionContext'

const LoginPage = lazy(() => import('@/pages/auth/LoginPage'))
const RegisterPage = lazy(() => import('@/pages/auth/RegisterPage'))
const OAuthCallbackPage = lazy(() => import('@/pages/auth/OAuthCallbackPage'))
const DashboardPage = lazy(() => import('@/pages/dashboard/DashboardPage'))
const InstitutionsPage = lazy(() => import('@/pages/institutions/InstitutionsPage'))
const LaboratoriesPage = lazy(() => import('@/pages/laboratories/LaboratoriesPage'))
const EquipmentModelsPage = lazy(() => import('@/pages/equipment-models/EquipmentModelsPage'))
const AcademicPeriodsPage = lazy(() => import('@/pages/academic-periods/AcademicPeriodsPage'))
const AcademicPeriodDetailPage = lazy(
  () => import('@/pages/academic-periods/AcademicPeriodDetailPage'),
)
const OccupationEditorPage = lazy(() => import('@/pages/academic-periods/OccupationEditorPage'))
const EmissionsDashboardPage = lazy(() => import('@/pages/emissions/EmissionsDashboardPage'))
const EmissionFactorsPage = lazy(() => import('@/pages/emission-factors/EmissionFactorsPage'))
const LongitudinalPage = lazy(() => import('@/pages/longitudinal/LongitudinalPage'))
const UsersPage = lazy(() => import('@/pages/users/UsersPage'))
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'))

export const App: React.FC = () => (
  <ErrorBoundary>
    <AuthProvider>
      <InstitutionProvider>
        <Suspense>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
            <Route element={<ProtectedRoute />}>
              <Route element={<AppLayout />}>
                <Route index element={<Navigate to="/dashboard" replace />} />
                <Route path="/dashboard" element={<DashboardPage />} />
                <Route path="/institutions" element={<InstitutionsPage />} />
                <Route path="/laboratories" element={<LaboratoriesPage />} />
                <Route path="/equipment-models" element={<EquipmentModelsPage />} />
                <Route path="/academic-periods" element={<AcademicPeriodsPage />} />
                <Route path="/academic-periods/:id" element={<AcademicPeriodDetailPage />} />
                <Route path="/academic-periods/:id/occupation" element={<OccupationEditorPage />} />
                <Route path="/emissions" element={<EmissionsDashboardPage />} />
                <Route path="/longitudinal" element={<LongitudinalPage />} />
                <Route path="/emission-factors" element={<EmissionFactorsPage />} />
                <Route path="/users" element={<UsersPage />} />
              </Route>
            </Route>
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </Suspense>
        <Toaster position="bottom-right" />
      </InstitutionProvider>
    </AuthProvider>
  </ErrorBoundary>
)
