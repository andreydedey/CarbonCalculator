/**
 * Testes da lógica pura de institutionSchema.ts.
 *
 * Ver a nota em client/src/context/InstitutionContext.test.ts sobre por que
 * a lógica pura (antes do marcador `@pure-logic-boundary`) é extraída para
 * um `.ts` temporário e importada de verdade, em vez de reimplementada
 * aqui: essas funções são exatamente as que o schema Zod usa em
 * `superRefine`, então testá-las é testar a regra real do schema sem
 * depender de `node_modules` (o pacote `zod` não está instalado neste
 * ambiente de verificação).
 */
import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync, writeFileSync, rmSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { randomUUID } from "node:crypto"
import { fileURLToPath, pathToFileURL } from "node:url"

const SOURCE_PATH = fileURLToPath(new URL("./institutionSchema.ts", import.meta.url))
const BOUNDARY = "// @pure-logic-boundary"

async function loadPureLogic() {
  const source = readFileSync(SOURCE_PATH, "utf8")
  const boundaryIndex = source.indexOf(BOUNDARY)
  assert.notEqual(
    boundaryIndex,
    -1,
    "marcador @pure-logic-boundary não encontrado em institutionSchema.ts",
  )
  const pureSource = source.slice(0, boundaryIndex)
  const tempPath = join(tmpdir(), `institution-schema-pure-${randomUUID()}.ts`)
  writeFileSync(tempPath, pureSource, "utf8")
  try {
    return await import(pathToFileURL(tempPath).href)
  } finally {
    rmSync(tempPath, { force: true })
  }
}

function validInput(overrides: Partial<Record<string, string>> = {}) {
  return {
    name: "Universidade Federal do Pará",
    acronym: "UFPA",
    city: "Belém",
    state: "PA",
    laboratoryName: "LABCOMP-01",
    ...overrides,
  }
}

test("@spec:AC-001 Instituição criada com dados válidos — nome, sigla, cidade, UF e laboratório válidos não geram erros", async () => {
  const { validateInstitutionForm } = await loadPureLogic()

  const errors = validateInstitutionForm(validInput())

  assert.deepEqual(errors, {})
})

test("@spec:AC-001 Instituição criada com dados válidos — payload normalizado casa com o corpo esperado por createInstitution", async () => {
  const { normalizeInstitutionForm } = await loadPureLogic()

  const payload = normalizeInstitutionForm(
    validInput({ name: "  Universidade Federal do Pará  ", acronym: " UFPA ", city: " Belém " }),
  )

  assert.deepEqual(payload, {
    name: "Universidade Federal do Pará",
    acronym: "UFPA",
    city: "Belém",
    state: "PA",
    laboratory: { name: "LABCOMP-01" },
  })
})

test("@spec:AC-001 Instituição criada com dados válidos — cidade em branco vira undefined (campo opcional)", async () => {
  const { normalizeInstitutionForm } = await loadPureLogic()

  const payload = normalizeInstitutionForm(validInput({ city: "   " }))

  assert.equal(payload.city, undefined)
})

test("@spec:AC-003 UF inválida é rejeitada — UF fora da lista das 27 unidades federativas gera erro no campo state", async () => {
  const { validateInstitutionForm } = await loadPureLogic()

  const errors = validateInstitutionForm(validInput({ state: "XX" }))

  assert.equal(errors.state, "UF inválida. Selecione uma das 27 unidades federativas.")
})

test("@spec:AC-003 UF inválida é rejeitada — todas as 27 UFs oficiais são aceitas", async () => {
  const { BRAZILIAN_STATES, isBrazilianState } = await loadPureLogic()

  assert.equal(BRAZILIAN_STATES.length, 27)
  for (const state of BRAZILIAN_STATES as readonly string[]) {
    assert.equal(isBrazilianState(state), true)
  }
})

test("@spec:AC-003 UF inválida é rejeitada — string vazia não é uma UF válida", async () => {
  const { isBrazilianState } = await loadPureLogic()

  assert.equal(isBrazilianState(""), false)
})
