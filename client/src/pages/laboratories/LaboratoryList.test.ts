/**
 * Testes da lógica pura de LaboratoryList.tsx.
 *
 * Ver a nota em client/src/context/InstitutionContext.test.ts sobre por que
 * a lógica pura (antes do marcador `@pure-logic-boundary`) é extraída para
 * um `.ts` temporário e importada de verdade, em vez de reimplementada
 * aqui: o Node não carrega arquivos `.tsx` diretamente.
 */
import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync, writeFileSync, rmSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { randomUUID } from "node:crypto"
import { fileURLToPath, pathToFileURL } from "node:url"

const SOURCE_PATH = fileURLToPath(new URL("./LaboratoryList.tsx", import.meta.url))
const BOUNDARY = "// @pure-logic-boundary"

async function loadPureLogic() {
  const source = readFileSync(SOURCE_PATH, "utf8")
  const boundaryIndex = source.indexOf(BOUNDARY)
  assert.notEqual(
    boundaryIndex,
    -1,
    "marcador @pure-logic-boundary não encontrado em LaboratoryList.tsx",
  )
  const pureSource = source.slice(0, boundaryIndex)
  const tempPath = join(tmpdir(), `laboratory-list-pure-${randomUUID()}.ts`)
  writeFileSync(tempPath, pureSource, "utf8")
  try {
    return await import(pathToFileURL(tempPath).href)
  } finally {
    rmSync(tempPath, { force: true })
  }
}

test("@spec:AC-006 lista mostra apenas laboratórios ativos por padrão — sem toggle, nenhum parâmetro de inclusão é enviado", async () => {
  const { buildListQuery } = await loadPureLogic()

  assert.deepEqual(buildListQuery(false), {})
})

test("@spec:AC-007 laboratórios inativos podem ser incluídos na listagem — com toggle ligado, includeInactive é enviado", async () => {
  const { buildListQuery } = await loadPureLogic()

  assert.deepEqual(buildListQuery(true), { includeInactive: true })
})

test("@spec:AC-006 lista mostra apenas laboratórios ativos por padrão — laboratório ativo vira card com rótulo Ativo", async () => {
  const { toViewModel } = await loadPureLogic()

  const viewModel = toViewModel({ id: "lab-1", name: "LABCOMP-01", active: true })

  assert.deepEqual(viewModel, {
    id: "lab-1",
    name: "LABCOMP-01",
    active: true,
    statusLabel: "Ativo",
  })
})

test("@spec:AC-007 laboratórios inativos podem ser incluídos na listagem — laboratório inativo vira card com rótulo Inativo", async () => {
  const { toViewModel } = await loadPureLogic()

  const viewModel = toViewModel({ id: "lab-2", name: "LABCOMP-02", active: false })

  assert.deepEqual(viewModel, {
    id: "lab-2",
    name: "LABCOMP-02",
    active: false,
    statusLabel: "Inativo",
  })
})
