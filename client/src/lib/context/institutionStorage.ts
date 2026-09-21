const COOKIE_NAME = 'institution_id'
const MAX_AGE_DAYS = 365

export function readStoredInstitutionId(): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${COOKIE_NAME}=([^;]*)`))
  return match?.[1] ?? null
}

export function writeStoredInstitutionId(institutionId: string | null): void {
  if (institutionId) {
    // biome-ignore lint/suspicious/noDocumentCookie: intentional cookie management, Cookie Store API has limited browser support
    document.cookie = `${COOKIE_NAME}=${institutionId}; path=/; max-age=${MAX_AGE_DAYS * 86400}; SameSite=Strict`
  } else {
    // biome-ignore lint/suspicious/noDocumentCookie: intentional cookie management, Cookie Store API has limited browser support
    document.cookie = `${COOKIE_NAME}=; path=/; max-age=0`
  }
}
