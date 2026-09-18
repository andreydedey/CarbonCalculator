/**
 * Testes da lógica pura de AppLayout.tsx.
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

const SOURCE_PATH = fileURLToPath(new URL("./AppLayout.tsx", import.meta.url))
const BOUNDARY = "// @pure-logic-boundary"

async function loadPureLogic() {
  const source = readFileSync(SOURCE_PATH, "utf8")
  const boundaryIndex = source.indexOf(BOUNDARY)
  assert.notEqual(
    boundaryIndex,
    -1,
    "marcador @pure-logic-boundary não encontrado em AppLayout.tsx",
  )
  const pureSource = source.slice(0, boundaryIndex)
  const tempPath = join(tmpdir(), `app-layout-pure-${randomUUID()}.ts`)
  writeFileSync(tempPath, pureSource, "utf8")
  try {
    return await import(pathToFileURL(tempPath).href)
  } finally {
    rmSync(tempPath, { force: true })
  }
}

test("@spec:AC-009 requisição sem identificação de instituição é recusada — layout fica bloqueado sem instituição ativa", async () => {
  const { resolveLayoutView } = await loadPureLogic()

  assert.equal(resolveLayoutView(false), "blocked")
})

test("@spec:AC-009 requisição sem identificação de instituição é recusada — layout libera conteúdo só com instituição ativa", async () => {
  const { resolveLayoutView } = await loadPureLogic()

  assert.equal(resolveLayoutView(true), "content")
})
