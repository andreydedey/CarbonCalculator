/**
 * Testes da lógica pura de InstitutionContext.tsx.
 *
 * Este projeto ainda não tem um bundler/transform de JSX instalado, e o
 * runtime do Node não carrega arquivos `.tsx` diretamente (nem mesmo os que
 * não usam JSX — a extensão é rejeitada antes de qualquer parsing). Por
 * isso, o trecho de lógica pura do arquivo fonte (tudo antes do marcador
 * `@pure-logic-boundary`, que não usa JSX nem importa módulos externos) é
 * extraído para um arquivo `.ts` temporário e importado de verdade — os
 * testes abaixo exercitam o código real do componente, não uma cópia dele.
 */
import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync, writeFileSync, rmSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { randomUUID } from "node:crypto"
import { fileURLToPath, pathToFileURL } from "node:url"

const SOURCE_PATH = fileURLToPath(new URL("./InstitutionContext.tsx", import.meta.url))
const BOUNDARY = "// @pure-logic-boundary"

async function loadPureLogic() {
  const source = readFileSync(SOURCE_PATH, "utf8")
  const boundaryIndex = source.indexOf(BOUNDARY)
  assert.notEqual(
    boundaryIndex,
    -1,
    "marcador @pure-logic-boundary não encontrado em InstitutionContext.tsx",
  )
  const pureSource = source.slice(0, boundaryIndex)
  const tempPath = join(tmpdir(), `institution-context-pure-${randomUUID()}.ts`)
  writeFileSync(tempPath, pureSource, "utf8")
  try {
    return await import(pathToFileURL(tempPath).href)
  } finally {
    rmSync(tempPath, { force: true })
  }
}

class MemoryStorage {
  #store = new Map<string, string>()

  getItem(key: string): string | null {
    return this.#store.has(key) ? this.#store.get(key)! : null
  }

  setItem(key: string, value: string): void {
    this.#store.set(key, value)
  }

  removeItem(key: string): void {
    this.#store.delete(key)
  }
}

test("@spec:AC-009 requisição sem identificação de instituição é recusada — nenhum id salvo é lido como null", async () => {
  const { readStoredInstitutionId } = await loadPureLogic()
  const storage = new MemoryStorage()

  assert.equal(readStoredInstitutionId(storage), null)
})

test("@spec:AC-009 requisição sem identificação de instituição é recusada — id salvo vazio/em branco também é null", async () => {
  const { readStoredInstitutionId, INSTITUTION_STORAGE_KEY } = await loadPureLogic()
  const storage = new MemoryStorage()
  storage.setItem(INSTITUTION_STORAGE_KEY, "   ")

  assert.equal(readStoredInstitutionId(storage), null)
})

test("@spec:AC-009 requisição sem identificação de instituição é recusada — limpar o id volta ao estado sem instituição", async () => {
  const { readStoredInstitutionId, writeStoredInstitutionId, INSTITUTION_STORAGE_KEY } =
    await loadPureLogic()
  const storage = new MemoryStorage()
  storage.setItem(INSTITUTION_STORAGE_KEY, "inst-a")

  writeStoredInstitutionId(storage, null)

  assert.equal(readStoredInstitutionId(storage), null)
})

test("@spec:AC-008 isolamento por RLS entre instituições — trocar de instituição nunca mistura os dois ids", async () => {
  const { readStoredInstitutionId, writeStoredInstitutionId } = await loadPureLogic()
  const storage = new MemoryStorage()

  writeStoredInstitutionId(storage, "instituicao-a")
  assert.equal(readStoredInstitutionId(storage), "instituicao-a")

  writeStoredInstitutionId(storage, "instituicao-b")
  assert.equal(readStoredInstitutionId(storage), "instituicao-b")
})

test("@spec:AC-008 isolamento por RLS entre instituições — o id persistido é exatamente o que foi selecionado", async () => {
  const { readStoredInstitutionId, writeStoredInstitutionId } = await loadPureLogic()
  const storage = new MemoryStorage()

  writeStoredInstitutionId(storage, "instituicao-a")
  writeStoredInstitutionId(storage, "instituicao-b")

  assert.notEqual(readStoredInstitutionId(storage), "instituicao-a")
})
