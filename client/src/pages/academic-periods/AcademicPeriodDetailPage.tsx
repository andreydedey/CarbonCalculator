import type React from 'react'
import { Navigate, useParams } from 'react-router-dom'

export const AcademicPeriodDetailPage: React.FC = () => {
  const { id: _id } = useParams<{ id: string }>()
  return <Navigate to="/academic-periods" replace />
}
