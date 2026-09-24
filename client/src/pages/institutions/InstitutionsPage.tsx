import { useInfiniteQuery } from '@tanstack/react-query'
import { Plus, Search } from 'lucide-react'
import type React from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useDebounce } from 'use-debounce'
import { InstitutionCard } from '@/components/institutions/InstitutionCard'
import { InstitutionFormDialog } from '@/components/institutions/InstitutionFormDialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { LoadMoreButton } from '@/components/ui/load-more-button'
import { useAuth } from '@/context/AuthContext'
import { useInstitution } from '@/context/InstitutionContext'
import { useDialog } from '@/hooks/use-dialog'
import { type Institution, listInstitutions } from '@/lib/api/institutions'

export const InstitutionsPage: React.FC = () => {
  const { user } = useAuth()
  const { setInstitutionId } = useInstitution()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const formDialog = useDialog<Institution>()

  const search = searchParams.get('q') ?? ''
  const [debouncedSearch] = useDebounce(search, 400)

  function setSearch(value: string) {
    setSearchParams(
      (prev) => {
        if (value) prev.set('q', value)
        else prev.delete('q')
        return prev
      },
      { replace: true },
    )
  }

  const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage, refetch } =
    useInfiniteQuery({
      queryKey: ['institutions', debouncedSearch],
      queryFn: ({ pageParam }) => listInstitutions({ search: debouncedSearch, page: pageParam }),
      initialPageParam: 0,
      getNextPageParam: (lastPage) =>
        lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
    })

  const institutions = data?.pages.flatMap((p) => p.content) ?? []

  function handleEnter(institution: Institution) {
    setInstitutionId(institution.id)
    navigate('/laboratories')
  }

  function handleSaved() {
    formDialog.closeDialog()
    refetch()
  }

  const isAdmin = user?.admin ?? false

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="font-heading text-2xl font-bold">Instituições</h1>
          <p className="text-sm text-muted-foreground">
            Selecione uma instituição para gerenciar ou crie uma nova.
          </p>
        </div>
        {isAdmin && (
          <Button onClick={() => formDialog.openDialog()}>
            <Plus className="size-4" />
            Nova Instituição
          </Button>
        )}
      </div>

      <div className="flex items-center gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Buscar instituição..."
            className="h-9 pl-8"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Carregando...</p>
      ) : institutions.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhuma instituição encontrada.</p>
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {institutions.map((institution) => (
            <InstitutionCard
              key={institution.id}
              institution={institution}
              onEnter={handleEnter}
              onEdit={isAdmin ? (inst) => formDialog.openDialog(inst) : undefined}
            />
          ))}
        </div>
      )}

      <LoadMoreButton
        fetchNextPage={fetchNextPage}
        hasNextPage={hasNextPage}
        isFetchingNextPage={isFetchingNextPage}
      />

      <InstitutionFormDialog
        institution={formDialog.data ?? undefined}
        open={formDialog.open}
        onOpenChange={(open) => !open && formDialog.closeDialog()}
        onSaved={handleSaved}
      />
    </div>
  )
}
