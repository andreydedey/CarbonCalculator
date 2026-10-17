const EMISSION_FORMAT = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 3,
  maximumFractionDigits: 3,
})

/** Emission in kg CO₂ with three decimals (grams), e.g. 1242.5 → "1.242,500". */
export function formatEmissionKg(kg: number): string {
  return EMISSION_FORMAT.format(kg)
}
