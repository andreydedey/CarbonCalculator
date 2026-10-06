import type React from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from '@/components/auth/ProtectedRoute'
import { ErrorBoundary } from '@/components/ErrorBoundary'
import { AppLayout } from '@/components/layout/AppLayout'
import { Toaster } from '@/components/ui/sonner'
import { AuthProvider } from '@/context/AuthContext'
import { InstitutionProvider } from '@/context/InstitutionContext'
import { AcademicPeriodDetailPage } from '@/pages/academic-periods/AcademicPeriodDetailPage'
import { AcademicPeriodsPage } from '@/pages/academic-periods/AcademicPeriodsPage'
import { OccupationEditorPage } from '@/pages/academic-periods/OccupationEditorPage'
import { LoginPage } from '@/pages/auth/LoginPage'
import { RegisterPage } from '@/pages/auth/RegisterPage'
import { DashboardPage } from '@/pages/dashboard/DashboardPage'
import { EmissionFactorsPage } from '@/pages/emission-factors/EmissionFactorsPage'
import { EmissionsDashboardPage } from '@/pages/emissions/EmissionsDashboardPage'
import { EquipmentModelsPage } from '@/pages/equipment-models/EquipmentModelsPage'
import { InstitutionsPage } from '@/pages/institutions/InstitutionsPage'
import { LaboratoriesPage } from '@/pages/laboratories/LaboratoriesPage'
import { UsersPage } from '@/pages/users/UsersPage'

export const App: React.FC = () => (
  <ErrorBoundary>
    <AuthProvider>
      <InstitutionProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
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
              <Route path="/emission-factors" element={<EmissionFactorsPage />} />
              <Route path="/users" element={<UsersPage />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
        <Toaster position="bottom-right" />
      </InstitutionProvider>
    </AuthProvider>
  </ErrorBoundary>
)
