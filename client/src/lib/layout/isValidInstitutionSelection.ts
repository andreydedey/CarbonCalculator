export interface InstitutionOption {
  id: string
  name: string
}

export function isValidInstitutionSelection(
  institutionId: string,
  options: readonly InstitutionOption[],
): boolean {
  return options.some((option) => option.id === institutionId)
}
