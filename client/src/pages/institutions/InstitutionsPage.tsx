import { useInfiniteQuery, useMutation } from '@tanstack/react-query'
import { Building2, Plus, Search } from 'lucide-react'
import type React from 'react'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { useDebounce } from 'use-debounce'
import { InstitutionCard } from '@/components/institutions/InstitutionCard'
import { InstitutionFormDialog } from '@/components/institutions/InstitutionFormDialog'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from '@/components/ui/empty'
import { Input } from '@/components/ui/input'
import { LoadMoreButton } from '@/components/ui/load-more-button'
import { useAuth } from '@/context/AuthContext'
import { useInstitution } from '@/context/InstitutionContext'
import { useDialog } from '@/hooks/use-dialog'
import { getInstitution, type Institution, listInstitutions } from '@/lib/api/institutions'

export const InstitutionsPage: React.FC = () => {
  const { user } = useAuth()
  const { institutionId, setInstitutionId } = useInstitution()
  const [searchParams, setSearchParams] = useSearchParams()
  const formDialog = useDialog<Institution>()
  const [pendingInstitution, setPendingInstitution] = useState<Institution | null>(null)

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

  const selectMutation = useMutation({
    mutationFn: (institution: Institution) => getInstitution(institution.id),
    onSuccess: (_, institution) => {
      setInstitutionId(institution.id)
      toast.success(
        pendingInstitution
          ? `Você trocou para ${institution.acronym}.`
          : `Você entrou em ${institution.acronym}.`,
      )
      setPendingInstitution(null)
    },
    onError: () => {
      toast.error('Não foi possível acessar esta instituição.')
      setPendingInstitution(null)
    },
  })

  function handleEnter(institution: Institution) {
    if (institutionId && institutionId !== institution.id) {
      setPendingInstitution(institution)
      return
    }
    selectMutation.mutate(institution)
  }

  function confirmSwitch() {
    if (!pendingInstitution) return
    selectMutation.mutate(pendingInstitution)
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
        search.length > 0 ? (
          <Empty>
            <EmptyHeader>
              <EmptyMedia>
                <Search className="size-6 text-muted-foreground" />
              </EmptyMedia>
              <EmptyTitle>Nenhum resultado encontrado</EmptyTitle>
              <EmptyDescription>Tente ajustar os filtros ou o termo de busca.</EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : (
          <Empty>
            <EmptyHeader>
              <EmptyMedia>
                <Building2 className="size-6 text-muted-foreground" />
              </EmptyMedia>
              <EmptyTitle>Nenhuma instituição cadastrada</EmptyTitle>
              <EmptyDescription>Comece cadastrando a primeira instituição.</EmptyDescription>
            </EmptyHeader>
            {isAdmin && (
              <EmptyContent>
                <Button onClick={() => formDialog.openDialog()}>Cadastrar Instituição</Button>
              </EmptyContent>
            )}
          </Empty>
        )
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {institutions.map((institution) => (
            <InstitutionCard
              key={institution.id}
              institution={institution}
              isCurrent={institution.id === institutionId}
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

      <AlertDialog
        open={!!pendingInstitution}
        onOpenChange={(open) => !open && setPendingInstitution(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Trocar de instituição</AlertDialogTitle>
            <AlertDialogDescription>
              Você está prestes a trocar para{' '}
              <span className="font-medium text-foreground">
                {pendingInstitution?.acronym} — {pendingInstitution?.name}
              </span>
              . Deseja continuar?
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancelar</AlertDialogCancel>
            <AlertDialogAction onClick={confirmSwitch}>Trocar</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
