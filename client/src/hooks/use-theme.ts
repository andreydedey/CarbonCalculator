import { useCallback, useSyncExternalStore } from 'react'

type Theme = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'theme'

function getStoredTheme(): Theme {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored === 'light' || stored === 'dark' || stored === 'system') return stored
  } catch {
    // SSR or storage unavailable
  }
  return 'system'
}

function resolveTheme(theme: Theme): 'light' | 'dark' {
  if (theme === 'system') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }
  return theme
}

function applyTheme(theme: Theme) {
  document.documentElement.classList.toggle('dark', resolveTheme(theme) === 'dark')
}

// Apply the stored theme immediately on module load — no useEffect needed,
// the class is set before React renders anything.
applyTheme(getStoredTheme())

// Re-apply when the OS preference changes (only matters when theme === 'system').
window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
  if (getStoredTheme() === 'system') {
    applyTheme('system')
    for (const l of listeners) l()
  }
})

let listeners: Array<() => void> = []

function subscribe(listener: () => void) {
  listeners = [...listeners, listener]
  return () => {
    listeners = listeners.filter((l) => l !== listener)
  }
}

export function useTheme() {
  const theme = useSyncExternalStore(subscribe, getStoredTheme, () => 'system' as Theme)

  const setTheme = useCallback((next: Theme) => {
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // storage unavailable
    }
    applyTheme(next)
    for (const l of listeners) l()
  }, [])

  return { theme, resolvedTheme: resolveTheme(theme), setTheme } as const
}
