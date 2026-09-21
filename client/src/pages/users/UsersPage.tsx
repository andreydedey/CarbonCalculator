import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { UserPlus } from 'lucide-react'
import type React from 'react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useAuth } from '@/context/AuthContext'
import {
  type UserMember,
  changeRole,
  inviteUser,
  listMembers,
  revokeAccess,
} from '@/lib/api/users'
import { ConflictError } from '@/lib/api/client'

const ROLE_LABELS: Record<string, string> = {
  GESTOR: 'Gestor',
  PESQUISADOR: 'Pesquisador',
}

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Ativo',
  PENDING: 'Pendente',
}

export const UsersPage: React.FC = () => {
  const { user: currentUser } = useAuth()
  const queryClient = useQueryClient()
  const [inviteOpen, setInviteOpen] = useState(false)
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteRole, setInviteRole] = useState('PESQUISADOR')

  const { data: members = [] } = useQuery({
    queryKey: ['users'],
    queryFn: listMembers,
  })

  const inviteMutation = useMutation({
    mutationFn: () => inviteUser({ email: inviteEmail, role: inviteRole }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      setInviteOpen(false)
      setInviteEmail('')
      setInviteRole('PESQUISADOR')
      toast.success('Convite enviado')
    },
    onError: (err) => {
      if (err instanceof ConflictError) {
        toast.error('Este usuário já foi convidado')
      } else {
        toast.error('Erro ao enviar convite')
      }
    },
  })

  const changeRoleMutation = useMutation({
    mutationFn: ({ id, role }: { id: string; role: string }) => changeRole(id, { role }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      toast.success('Papel alterado')
    },
    onError: () => {
      toast.error('Erro ao alterar papel')
    },
  })

  const revokeMutation = useMutation({
    mutationFn: (id: string) => revokeAccess(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      toast.success('Acesso revogado')
    },
    onError: () => {
      toast.error('Erro ao revogar acesso')
    },
  })

  const isSelf = (member: UserMember) =>
    member.email === currentUser?.email

  const gestorCount = members.filter(
    (m) => m.role === 'GESTOR' && m.status === 'ACTIVE',
  ).length
  const pesquisadorCount = members.filter(
    (m) => m.role === 'PESQUISADOR' && m.status === 'ACTIVE',
  ).length

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-heading text-xl font-bold">Gestão de Usuários</h1>
          <p className="text-muted-foreground text-sm">
            Gerencie os membros da instituição
          </p>
        </div>

        <Dialog open={inviteOpen} onOpenChange={setInviteOpen}>
          <DialogTrigger asChild>
            <Button size="sm">
              <UserPlus className="mr-2 h-4 w-4" />
              Convidar
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Convidar Membro</DialogTitle>
            </DialogHeader>
            <form
              onSubmit={(e) => {
                e.preventDefault()
                inviteMutation.mutate()
              }}
              className="space-y-4"
            >
              <div className="space-y-2">
                <Label htmlFor="invite-email">Email</Label>
                <Input
                  id="invite-email"
                  type="email"
                  placeholder="email@universidade.br"
                  value={inviteEmail}
                  onChange={(e) => setInviteEmail(e.target.value)}
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="invite-role">Papel</Label>
                <Select value={inviteRole} onValueChange={setInviteRole}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="GESTOR">Gestor</SelectItem>
                    <SelectItem value="PESQUISADOR">Pesquisador</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <Button type="submit" className="w-full" disabled={inviteMutation.isPending}>
                {inviteMutation.isPending ? 'Enviando...' : 'Enviar Convite'}
              </Button>
            </form>
          </DialogContent>
        </Dialog>
      </div>

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
            <p className="text-2xl font-bold">{gestorCount}</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              Pesquisadores
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-2xl font-bold">{pesquisadorCount}</p>
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
                      <Badge
                        variant={member.role === 'GESTOR' ? 'default' : 'secondary'}
                      >
                        {ROLE_LABELS[member.role] ?? member.role}
                      </Badge>
                    </td>
                    <td className="px-4 py-3">
                      <Badge
                        variant={member.status === 'ACTIVE' ? 'outline' : 'secondary'}
                      >
                        {STATUS_LABELS[member.status] ?? member.status}
                      </Badge>
                    </td>
                    <td className="px-4 py-3">
                      {!isSelf(member) && (
                        <div className="flex gap-2">
                          <Select
                            value={member.role}
                            onValueChange={(role) =>
                              changeRoleMutation.mutate({ id: member.id, role })
                            }
                          >
                            <SelectTrigger className="h-7 w-[130px] text-xs">
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="GESTOR">Gestor</SelectItem>
                              <SelectItem value="PESQUISADOR">Pesquisador</SelectItem>
                            </SelectContent>
                          </Select>
                          <Button
                            variant="destructive"
                            size="sm"
                            className="h-7 text-xs"
                            onClick={() => revokeMutation.mutate(member.id)}
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
                    <td
                      colSpan={5}
                      className="px-4 py-8 text-center text-muted-foreground"
                    >
                      Nenhum membro encontrado
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
