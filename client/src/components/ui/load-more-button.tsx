import { ChevronsDown, Loader2 } from 'lucide-react'
import type React from 'react'
import { Button } from '@/components/ui/button'

interface LoadMoreButtonProps {
  fetchNextPage: () => void
  hasNextPage: boolean
  isFetchingNextPage: boolean
}

export const LoadMoreButton: React.FC<LoadMoreButtonProps> = ({
  fetchNextPage,
  hasNextPage,
  isFetchingNextPage,
}) => {
  if (!hasNextPage) return null

  return (
    <Button
      variant="outline"
      className="w-full"
      onClick={fetchNextPage}
      disabled={isFetchingNextPage}
    >
      {isFetchingNextPage ? (
        <Loader2 className="size-4 animate-spin" />
      ) : (
        <ChevronsDown className="size-4" />
      )}
      Carregar mais
    </Button>
  )
}
