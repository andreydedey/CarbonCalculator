export interface ApiErrorLike {
  status: number
  message: string
}

function isApiErrorLike(error: unknown): error is ApiErrorLike {
  return (
    typeof error === 'object' &&
    error !== null &&
    'status' in error &&
    typeof (error as { status: unknown }).status === 'number' &&
    'message' in error &&
    typeof (error as { message: unknown }).message === 'string'
  )
}

export interface SubmitFormError {
  field: 'acronym' | 'root'
  message: string
}

export function mapCreateInstitutionError(error: unknown): SubmitFormError {
  if (isApiErrorLike(error) && error.status === 409) {
    return {
      field: 'acronym',
      message: error.message || 'Sigla já está em uso por outra instituição.',
    }
  }
  if (isApiErrorLike(error)) {
    return {
      field: 'root',
      message: error.message || 'Não foi possível salvar a instituição. Revise os dados.',
    }
  }
  return {
    field: 'root',
    message: 'Não foi possível salvar a instituição. Tente novamente.',
  }
}
