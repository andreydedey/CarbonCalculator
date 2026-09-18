/**
 * Testes da lógica pura de InstitutionSwitcher.tsx.
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

const SOURCE_PATH = fileURLToPath(new URL("./InstitutionSwitcher.tsx", import.meta.url))
const BOUNDARY = "// @pure-logic-boundary"

async function loadPureLogic() {
  const source = readFileSync(SOURCE_PATH, "utf8")
  const boundaryIndex = source.indexOf(BOUNDARY)
  assert.notEqual(
    boundaryIndex,
    -1,
    "marcador @pure-logic-boundary não encontrado em InstitutionSwitcher.tsx",
  )
  const pureSource = source.slice(0, boundaryIndex)
  const tempPath = join(tmpdir(), `institution-switcher-pure-${randomUUID()}.ts`)
  writeFileSync(tempPath, pureSource, "utf8")
  try {
    return await import(pathToFileURL(tempPath).href)
  } finally {
    rmSync(tempPath, { force: true })
  }
}

test("@spec:AC-008 isolamento por RLS entre instituições — só aceita trocar para instituição realmente listada", async () => {
  const { isValidInstitutionSelection } = await loadPureLogic()
  const options = [
    { id: "instituicao-a", name: "Instituição A" },
    { id: "instituicao-b", name: "Instituição B" },
  ]

  assert.equal(isValidInstitutionSelection("instituicao-a", options), true)
  assert.equal(isValidInstitutionSelection("instituicao-de-outro-usuario", options), false)
})

test("@spec:AC-008 isolamento por RLS entre instituições — sem opções conhecidas, nenhuma seleção é aceita", async () => {
  const { isValidInstitutionSelection } = await loadPureLogic()

  assert.equal(isValidInstitutionSelection("qualquer-id", []), false)
})
