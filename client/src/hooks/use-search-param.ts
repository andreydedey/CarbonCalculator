import { useSearchParams } from 'react-router-dom'
import { useDebounce } from 'use-debounce'

export function useSearchParam(param = 'q', delay = 400) {
  const [searchParams, setSearchParams] = useSearchParams()

  const value = searchParams.get(param) ?? ''
  const [debouncedValue] = useDebounce(value, delay)

  function setValue(next: string) {
    setSearchParams(
      (prev) => {
        if (next) prev.set(param, next)
        else prev.delete(param)
        return prev
      },
      { replace: true },
    )
  }

  return [value, debouncedValue, setValue] as const
}
