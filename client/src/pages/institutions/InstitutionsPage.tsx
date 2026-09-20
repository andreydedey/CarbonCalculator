import type React from 'react'
import { InstitutionForm } from '@/components/institutions/InstitutionForm'
import { AppLayout } from '@/components/layout/AppLayout'

export const InstitutionsPage: React.FC = () => (
  <AppLayout requireInstitution={false}>
    <InstitutionForm />
  </AppLayout>
)
