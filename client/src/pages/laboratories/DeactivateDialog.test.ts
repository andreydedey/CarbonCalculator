/**
 * Testes da lógica pura de DeactivateDialog.tsx.
 *
 * Este projeto ainda não tem um bundler/transform de JSX instalado, e o
 * runtime do Node não carrega arquivos `.tsx` diretamente. Por isso, o
 * trecho de lógica pura do arquivo fonte (tudo antes do marcador
 * `@pure-logic-boundary`, que não usa JSX nem importa módulos externos) é
 * extraído para um arquivo `.ts` temporário e importado de verdade — os
 * testes abaixo exercitam o código real do componente, não uma cópia dele.
 */
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { randomUUID } from 'node:crypto'
import { fileURLToPath, pathToFileURL } from 'node:url'

const SOURCE_PATH = fileURLToPath(new URL('./DeactivateDialog.tsx', import.meta.url))
const BOUNDARY = '// @pure-logic-boundary'

async function loadPureLogic() {
  const source = readFileSync(SOURCE_PATH, 'utf8')
  const boundaryIndex = source.indexOf(BOUNDARY)
  assert.notEqual(
    boundaryIndex,
    -1,
    'marcador @pure-logic-boundary não encontrado em DeactivateDialog.tsx',
  )
  const pureSource = source.slice(0, boundaryIndex)
  const tempPath = join(tmpdir(), `deactivate-dialog-pure-${randomUUID()}.ts`)
  writeFileSync(tempPath, pureSource, 'utf8')
  try {
    return await import(pathToFileURL(tempPath).href)
  } finally {
    rmSync(tempPath, { force: true })
  }
}

test('@spec:AC-011 desativação preserva o laboratório — mensagem cita o nome do laboratório', async () => {
  const { buildDeactivateConfirmationMessage } = await loadPureLogic()

  const message = buildDeactivateConfirmationMessage('LABCOMP-01')

  assert.match(message, /LABCOMP-01/)
})

test('@spec:AC-011 desativação preserva o laboratório — mensagem afirma que os dados são preservados', async () => {
  const { buildDeactivateConfirmationMessage } = await loadPureLogic()

  const message = buildDeactivateConfirmationMessage('LABCOMP-01')

  assert.match(message, /preservad/i)
})

test('@spec:AC-011 desativação preserva o laboratório — mensagem não menciona exclusão/remoção permanente', async () => {
  const { buildDeactivateConfirmationMessage } = await loadPureLogic()

  const message = buildDeactivateConfirmationMessage('LABCOMP-01').toLowerCase()

  assert.equal(message.includes('exclu'), false)
  assert.equal(message.includes('remov'), false)
  assert.equal(message.includes('apagad'), false)
})
