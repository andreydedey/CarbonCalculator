import { useInfiniteQuery, type QueryKey } from '@tanstack/react-query'
import type { PageResponse } from '@/lib/api/types'

interface UsePagedQueryOptions<T> {
  queryKey: QueryKey
  queryFn: (page: number) => Promise<PageResponse<T>>
  enabled?: boolean
}

export function usePagedQuery<T>({ queryKey, queryFn, enabled }: UsePagedQueryOptions<T>) {
  const query = useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) => queryFn(pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
    enabled,
  })

  const items = query.data?.pages.flatMap((p) => p.content) ?? []

  return { ...query, items }
}
