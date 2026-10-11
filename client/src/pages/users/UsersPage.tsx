import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Loader2, RotateCw, UserPlus, Users } from 'lucide-react'
import type React from 'react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { MetricCardSkeleton, TableSkeleton } from '@/components/ui/skeletons'
import { InviteDialog } from '@/components/users/InviteDialog'
import { InviteLinkDialog } from '@/components/users/InviteLinkDialog'
import { RevokeAccessDialog } from '@/components/users/RevokeAccessDialog'
import { useAuth } from '@/context/AuthContext'
import { useDialog } from '@/hooks/use-dialog'
import { ApiError } from '@/lib/api/client'
import { changeRole, inviteUser, listMembers, resendInvite, type UserMember } from '@/lib/api/users'
import { type InviteFormData, inviteSchema } from '@/lib/schemas/inviteSchema'

const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Admin Global',
  MANAGER: 'Gestor',
  RESEARCHER: 'Pesquisador',
}

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Ativo',
  PENDING: 'Pendente',
}

export const UsersPage: React.FC = () => {
  const { user: currentUser } = useAuth()
  const inviteDialog = useDialog()
  const revokeDialog = useDialog<UserMember>()
  const [inviteLink, setInviteLink] = useState<string | null>(null)

  const {
    data: membersPage,
    refetch,
    isLoading,
  } = useQuery({
    queryKey: ['users'],
    queryFn: listMembers,
  })
  const members = membersPage?.content ?? []

  const inviteForm = useForm<InviteFormData>({
    resolver: zodResolver(inviteSchema),
    defaultValues: { email: '', role: 'RESEARCHER' },
  })

  const inviteMutation = useMutation({
    mutationFn: (data: InviteFormData) => inviteUser(data),
    onSuccess: (result) => {
      refetch()
      inviteDialog.closeDialog()
      inviteForm.reset()
      if (result.inviteLink) {
        const fullLink = `${window.location.origin}${result.inviteLink}`
        setInviteLink(fullLink)
      } else {
        toast.success('Membro adicionado')
      }
    },
    onError: (err) => {
      toast.error(err instanceof ApiError ? err.message : 'Erro ao enviar convite')
    },
  })

  const changeRoleMutation = useMutation({
    mutationFn: ({ id, role }: { id: string; role: string }) => changeRole(id, { role }),
    onSuccess: () => {
      refetch()
      toast.success('Papel alterado')
    },
    onError: (err) => {
      toast.error(err instanceof ApiError ? err.message : 'Erro ao alterar papel')
    },
  })

  const resendInviteMutation = useMutation({
    mutationFn: (id: string) => resendInvite(id),
    onSuccess: (result) => {
      refetch()
      if (result.inviteLink) {
        const fullLink = `${window.location.origin}${result.inviteLink}`
        setInviteLink(fullLink)
      }
    },
    onError: (err) => {
      toast.error(err instanceof ApiError ? err.message : 'Erro ao reenviar convite')
    },
  })

  const isSelf = (member: UserMember) => member.email === currentUser?.email

  const managerCount = members.filter((m) => m.role === 'MANAGER' && m.status === 'ACTIVE').length
  const researcherCount = members.filter(
    (m) => m.role === 'RESEARCHER' && m.status === 'ACTIVE',
  ).length

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-heading text-xl font-bold">Gestão de Usuários</h1>
          <p className="text-muted-foreground text-sm">Gerencie os membros da instituição</p>
        </div>

        <Button size="lg" onClick={() => inviteDialog.openDialog()}>
          <UserPlus className="mr-2 h-4 w-4" />
          Convidar
        </Button>
      </div>

      <InviteDialog
        open={inviteDialog.open}
        onOpenChange={(open) => {
          if (!open) {
            inviteDialog.closeDialog()
            inviteForm.reset()
          }
        }}
        form={inviteForm}
        onSubmit={(data) => inviteMutation.mutate(data)}
        isPending={inviteMutation.isPending}
        isAdmin={currentUser?.admin ?? false}
      />

      {revokeDialog.data && (
        <RevokeAccessDialog
          member={revokeDialog.data}
          open={revokeDialog.open}
          onOpenChange={(open) => !open && revokeDialog.closeDialog()}
          onRevoked={refetch}
        />
      )}

      <InviteLinkDialog link={inviteLink} onOpenChange={(open) => !open && setInviteLink(null)} />

      {isLoading ? (
        <div className="space-y-6">
          <MetricCardSkeleton count={3} />
          <TableSkeleton rows={5} columns={5} />
        </div>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-3">
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground">
                  Total de Membros
                </CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-2xl font-bold">{members.length}</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground">
                  Gestores
                </CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-2xl font-bold">{managerCount}</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground">
                  Pesquisadores
                </CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-2xl font-bold">{researcherCount}</p>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardContent className="p-0">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="px-4 py-3 font-medium">Nome</th>
                      <th className="px-4 py-3 font-medium">Email</th>
                      <th className="px-4 py-3 font-medium">Papel</th>
                      <th className="px-4 py-3 font-medium">Status</th>
                      <th className="px-4 py-3 font-medium">Ações</th>
                    </tr>
                  </thead>
                  <tbody>
                    {members.map((member) => (
                      <tr key={member.id} className="border-b last:border-0">
                        <td className="px-4 py-3">
                          {member.name ?? '—'}
                          {isSelf(member) && (
                            <span className="ml-1 text-muted-foreground">(você)</span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">{member.email}</td>
                        <td className="px-4 py-3">
                          {member.admin ? (
                            <Badge variant="default">Admin Global</Badge>
                          ) : (
                            <Badge variant={member.role === 'MANAGER' ? 'default' : 'secondary'}>
                              {ROLE_LABELS[member.role] ?? member.role}
                            </Badge>
                          )}
                        </td>
                        <td className="px-4 py-3">
                          <Badge variant={member.status === 'ACTIVE' ? 'outline' : 'secondary'}>
                            {STATUS_LABELS[member.status] ?? member.status}
                          </Badge>
                        </td>
                        <td className="px-4 py-3">
                          {!isSelf(member) && (
                            <div className="flex gap-2">
                              {member.status === 'PENDING' ? (
                                <Button
                                  variant="outline"
                                  size="sm"
                                  className="h-7 text-xs"
                                  disabled={resendInviteMutation.isPending}
                                  onClick={() => resendInviteMutation.mutate(member.id)}
                                >
                                  {resendInviteMutation.isPending &&
                                  resendInviteMutation.variables === member.id ? (
                                    <Loader2 className="mr-1 h-3 w-3 animate-spin" />
                                  ) : (
                                    <RotateCw className="mr-1 h-3 w-3" />
                                  )}
                                  Reenviar
                                </Button>
                              ) : (
                                <Select
                                  value={member.admin ? 'ADMIN' : member.role}
                                  onValueChange={(role) =>
                                    changeRoleMutation.mutate({ id: member.id, role })
                                  }
                                  disabled={member.admin}
                                >
                                  <SelectTrigger className="h-7 w-[140px] text-xs">
                                    <SelectValue />
                                  </SelectTrigger>
                                  <SelectContent>
                                    <SelectItem value="ADMIN" disabled={!currentUser?.admin}>
                                      Admin Global
                                    </SelectItem>
                                    <SelectItem value="MANAGER">Gestor</SelectItem>
                                    <SelectItem value="RESEARCHER">Pesquisador</SelectItem>
                                  </SelectContent>
                                </Select>
                              )}
                              <Button
                                variant="destructive"
                                size="sm"
                                className="h-7 text-xs"
                                onClick={() => revokeDialog.openDialog(member)}
                              >
                                Revogar
                              </Button>
                            </div>
                          )}
                        </td>
                      </tr>
                    ))}
                    {members.length === 0 && (
                      <tr>
                        <td colSpan={5} className="px-4 py-8">
                          <Empty>
                            <EmptyHeader>
                              <EmptyMedia>
                                <Users className="size-6 text-muted-foreground" />
                              </EmptyMedia>
                              <EmptyTitle>Nenhum membro cadastrado</EmptyTitle>
                              <EmptyDescription>
                                Convide o primeiro membro para a instituição.
                              </EmptyDescription>
                            </EmptyHeader>
                          </Empty>
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  )
}
