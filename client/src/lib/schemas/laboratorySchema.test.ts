/**
 * Testes da lógica pura de laboratorySchema.ts.
 *
 * Este projeto ainda não tem `node_modules` instalado neste worktree, e o
 * schema real usa `zod`. Por isso, o trecho de lógica pura do arquivo fonte
 * (tudo antes do marcador `@pure-logic-boundary`, que não importa nenhum
 * pacote externo) é extraído para um arquivo `.ts` temporário e importado
 * de verdade — os testes abaixo exercitam o código real usado pelo schema
 * (`laboratoryFormSchema` delega a validação a `validateLaboratoryName`),
 * não uma cópia dele.
 */
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { randomUUID } from 'node:crypto'
import { fileURLToPath, pathToFileURL } from 'node:url'

const SOURCE_PATH = fileURLToPath(new URL('./laboratorySchema.ts', import.meta.url))
const BOUNDARY = '// @pure-logic-boundary'

async function loadPureLogic() {
  const source = readFileSync(SOURCE_PATH, 'utf8')
  const boundaryIndex = source.indexOf(BOUNDARY)
  assert.notEqual(
    boundaryIndex,
    -1,
    'marcador @pure-logic-boundary não encontrado em laboratorySchema.ts',
  )
  const pureSource = source.slice(0, boundaryIndex)
  const tempPath = join(tmpdir(), `laboratory-schema-pure-${randomUUID()}.ts`)
  writeFileSync(tempPath, pureSource, 'utf8')
  try {
    return await import(pathToFileURL(tempPath).href)
  } finally {
    rmSync(tempPath, { force: true })
  }
}

test('@spec:AC-004 laboratório criado com nome — nome válido é aceito, sem espaços nas pontas', async () => {
  const { validateLaboratoryName } = await loadPureLogic()

  const result = validateLaboratoryName('  LABCOMP-01  ')

  assert.deepEqual(result, { valid: true, value: 'LABCOMP-01' })
})

test('@spec:AC-005 laboratório sem nome é rejeitado — nome vazio é recusado', async () => {
  const { validateLaboratoryName, LABORATORY_NAME_REQUIRED_ERROR } = await loadPureLogic()

  const result = validateLaboratoryName('')

  assert.deepEqual(result, { valid: false, error: LABORATORY_NAME_REQUIRED_ERROR })
})

test('@spec:AC-005 laboratório sem nome é rejeitado — nome só com espaços em branco é recusado', async () => {
  const { validateLaboratoryName } = await loadPureLogic()

  const result = validateLaboratoryName('    ')

  assert.equal(result.valid, false)
})
