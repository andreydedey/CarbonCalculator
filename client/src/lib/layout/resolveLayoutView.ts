export type LayoutView = 'blocked' | 'content'

export function resolveLayoutView(hasInstitution: boolean): LayoutView {
  return hasInstitution ? 'content' : 'blocked'
}

export interface NavItem {
  label: string
  href: string
}

export const NAV_ITEMS: readonly NavItem[] = [
  { label: 'Instituições', href: '/institutions' },
  { label: 'Laboratórios', href: '/laboratories' },
]
