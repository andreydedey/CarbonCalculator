export type LayoutView = 'blocked' | 'content'

export function resolveLayoutView(hasInstitution: boolean): LayoutView {
  return hasInstitution ? 'content' : 'blocked'
}
