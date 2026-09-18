/**
 * Testes da lógica pura de LaboratoryForm.tsx.
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

const SOURCE_PATH = fileURLToPath(new URL('./LaboratoryForm.tsx', import.meta.url))
const BOUNDARY = '// @pure-logic-boundary'

async function loadPureLogic() {
  const source = readFileSync(SOURCE_PATH, 'utf8')
  const boundaryIndex = source.indexOf(BOUNDARY)
  assert.notEqual(
    boundaryIndex,
    -1,
    'marcador @pure-logic-boundary não encontrado em LaboratoryForm.tsx',
  )
  const pureSource = source.slice(0, boundaryIndex)
  const tempPath = join(tmpdir(), `laboratory-form-pure-${randomUUID()}.ts`)
  writeFileSync(tempPath, pureSource, 'utf8')
  try {
    return await import(pathToFileURL(tempPath).href)
  } finally {
    rmSync(tempPath, { force: true })
  }
}

test('@spec:AC-004 laboratório criado com nome — payload traz o nome sem espaços nas pontas', async () => {
  const { buildLaboratoryPayload } = await loadPureLogic()

  const payload = buildLaboratoryPayload({ name: '  LABCOMP-02  ' })

  assert.deepEqual(payload, { name: 'LABCOMP-02' })
})

test('@spec:AC-004 laboratório criado com nome — modo é "create" quando não há laboratório carregado', async () => {
  const { resolveLaboratoryFormMode } = await loadPureLogic()

  assert.equal(resolveLaboratoryFormMode(undefined), 'create')
})

test('@spec:AC-004 laboratório criado com nome — modo é "edit" quando há um laboratório carregado', async () => {
  const { resolveLaboratoryFormMode } = await loadPureLogic()

  assert.equal(
    resolveLaboratoryFormMode({ id: 'lab-1', name: 'LABCOMP-01', active: true }),
    'edit',
  )
})
