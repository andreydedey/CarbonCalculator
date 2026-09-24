import type React from 'react'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useInstitution } from '@/context/InstitutionContext'

interface InstitutionOption {
  id: string
  name: string
}

interface InstitutionSwitcherProps {
  options?: readonly InstitutionOption[]
}

export const InstitutionSwitcher: React.FC<InstitutionSwitcherProps> = ({ options = [] }) => {
  const { institutionId, setInstitutionId } = useInstitution()

  const isValid = institutionId && options.some((o) => o.id === institutionId)
  if (options.length > 0 && !isValid) {
    setInstitutionId(options[0].id)
  }

  function handleChange(value: string) {
    if (options.some((option) => option.id === value)) {
      setInstitutionId(value)
    }
  }

  return (
    <Select value={institutionId ?? ''} onValueChange={handleChange}>
      <SelectTrigger aria-label="Instituição ativa">
        <SelectValue placeholder="Selecione uma instituição" />
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem key={option.id} value={option.id}>
            {option.name}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
