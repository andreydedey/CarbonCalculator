import type React from 'react'
import { LaboratoryList } from '@/components/laboratories/LaboratoryList'
import { AppLayout } from '@/components/layout/AppLayout'

export const LaboratoriesPage: React.FC = () => (
  <AppLayout>
    <LaboratoryList />
  </AppLayout>
)
