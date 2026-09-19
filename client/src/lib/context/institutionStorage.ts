export const INSTITUTION_STORAGE_KEY = 'carbon-calculator:institution-id'

export interface StorageLike {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

export function readStoredInstitutionId(storage: StorageLike): string | null {
  const raw = storage.getItem(INSTITUTION_STORAGE_KEY)
  return raw && raw.trim().length > 0 ? raw : null
}

export function writeStoredInstitutionId(storage: StorageLike, institutionId: string | null): void {
  if (institutionId && institutionId.trim().length > 0) {
    storage.setItem(INSTITUTION_STORAGE_KEY, institutionId)
  } else {
    storage.removeItem(INSTITUTION_STORAGE_KEY)
  }
}
