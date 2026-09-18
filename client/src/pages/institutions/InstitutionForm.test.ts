/**
 * Testes da lógica pura de InstitutionForm.tsx.
 *
 * Ver a nota em client/src/context/InstitutionContext.test.ts sobre por que
 * a lógica pura (antes do marcador `@pure-logic-boundary`) é extraída para
 * um `.ts` temporário e importada de verdade, em vez de reimplementada
 * aqui: o Node não carrega arquivos `.tsx` diretamente, nem há
 * `node_modules` instalado neste ambiente de verificação.
 */
import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync, writeFileSync, rmSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { randomUUID } from "node:crypto"
import { fileURLToPath, pathToFileURL } from "node:url"

const SOURCE_PATH = fileURLToPath(new URL("./InstitutionForm.tsx", import.meta.url))
const BOUNDARY = "// @pure-logic-boundary"

async function loadPureLogic() {
  const source = readFileSync(SOURCE_PATH, "utf8")
  const boundaryIndex = source.indexOf(BOUNDARY)
  assert.notEqual(
    boundaryIndex,
    -1,
    "marcador @pure-logic-boundary não encontrado em InstitutionForm.tsx",
  )
  const pureSource = source.slice(0, boundaryIndex)
  const tempPath = join(tmpdir(), `institution-form-pure-${randomUUID()}.ts`)
  writeFileSync(tempPath, pureSource, "utf8")
  try {
    return await import(pathToFileURL(tempPath).href)
  } finally {
    rmSync(tempPath, { force: true })
  }
}

test("@spec:AC-002 Sigla duplicada é rejeitada — erro 409 vira erro no campo Sigla com aviso de uso duplicado", async () => {
  const { mapCreateInstitutionError } = await loadPureLogic()

  const result = mapCreateInstitutionError({
    status: 409,
    message: "Sigla já está em uso por outra instituição.",
  })

  assert.deepEqual(result, {
    field: "acronym",
    message: "Sigla já está em uso por outra instituição.",
  })
})

test("@spec:AC-002 Sigla duplicada é rejeitada — mensagem padrão é usada quando o erro 409 não traz mensagem", async () => {
  const { mapCreateInstitutionError } = await loadPureLogic()

  const result = mapCreateInstitutionError({ status: 409, message: "" })

  assert.equal(result.field, "acronym")
  assert.equal(result.message.length > 0, true)
})

test("erro 400 (dados inválidos) vira aviso geral do formulário, não erro de campo", async () => {
  const { mapCreateInstitutionError } = await loadPureLogic()

  const result = mapCreateInstitutionError({ status: 400, message: "UF inválida" })

  assert.equal(result.field, "root")
  assert.equal(result.message, "UF inválida")
})

test("erro desconhecido (rede, etc.) vira aviso geral com mensagem padrão", async () => {
  const { mapCreateInstitutionError } = await loadPureLogic()

  const result = mapCreateInstitutionError(new Error("boom"))

  assert.equal(result.field, "root")
  assert.equal(result.message.length > 0, true)
})
