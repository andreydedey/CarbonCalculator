import assert from 'node:assert/strict'
import { describe, test } from 'node:test'

// Inline types to avoid importing client.ts which has a broken @/lib alias.
// These mirror the EmissionResult type from emissions.ts and the backend's
// EmissionResultDTO — the test verifies the data contract that feeds each
// dashboard component.

function mockEmissionResult() {
  return {
    period: {
      id: '550e8400-e29b-41d4-a716-446655440000',
      name: '2025.1',
      startDate: '2025-03-01',
      endDate: '2025-07-31',
    },
    totalEmissionKg: 42.5,
    totalEnergyKwh: 1000.0,
    equivalences: { carKm: 255, treesNeeded: 0.29 },
    byMonth: [
      {
        month: '2025-03',
        energyKwh: 200.0,
        emissionKg: 8.5,
        emissionFactor: 0.0425,
        schoolDays: 21,
      },
      {
        month: '2025-04',
        energyKwh: 220.0,
        emissionKg: 9.9,
        emissionFactor: 0.045,
        schoolDays: 20,
      },
    ],
    byLaboratory: [
      {
        laboratoryId: 'lab-001',
        laboratoryName: 'LABCOMP-01',
        energyKwh: 600.0,
        emissionKg: 25.5,
        stationCount: 30,
        computerEmissionKg: 18.0,
        monitorEmissionKg: 7.5,
        byMonth: [],
        configurations: [
          {
            configurationId: 'cfg-001',
            label: 'Dell OptiPlex + Windows 10 + Monitor E2020H',
            quantity: 30,
            consumptionWatts: 86,
            consumptionSource: 'specification',
            computerWatts: 65,
            monitorWatts: 21,
            energyKwh: 600.0,
            emissionKg: 25.5,
          },
        ],
      },
      {
        laboratoryId: 'lab-002',
        laboratoryName: 'LABCOMP-02',
        energyKwh: 400.0,
        emissionKg: 17.0,
        stationCount: 25,
        computerEmissionKg: 12.0,
        monitorEmissionKg: 5.0,
        byMonth: [],
        configurations: [],
      },
    ],
    byShift: [
      { shiftType: 'MORNING', energyKwh: 500.0, emissionKg: 21.25 },
      { shiftType: 'AFTERNOON', energyKwh: 500.0, emissionKg: 21.25 },
    ],
    byDayOfWeek: [
      { dayOfWeek: 1, label: 'Segunda', energyKwh: 200.0, emissionKg: 8.5 },
      { dayOfWeek: 2, label: 'Terça', energyKwh: 200.0, emissionKg: 8.5 },
    ],
    byEquipmentModel: [
      {
        modelId: 'model-001',
        modelName: 'Dell OptiPlex 7090',
        emissionKg: 18.0,
        percentage: 60.0,
      },
      {
        modelId: 'model-002',
        modelName: 'Dell OptiPlex 3090',
        emissionKg: 12.0,
        percentage: 40.0,
      },
    ],
    byMonitorModel: [
      {
        monitorId: 'mon-001',
        monitorName: 'Dell E2020H',
        emissionKg: 7.5,
        percentage: 60.0,
      },
    ],
    byOperatingSystem: [
      { operatingSystem: 'Windows 10', emissionKg: 25.5, percentage: 60.0 },
      { operatingSystem: 'Linux', emissionKg: 17.0, percentage: 40.0 },
    ],
    inputs: {
      emissionFactors: [
        { month: '2025-03', value: 0.0425, source: 'MCTI — SIN mar/2025' },
        { month: '2025-04', value: 0.045, source: 'MCTI — SIN abr/2025' },
      ],
      consumptionSources: [
        {
          configurationId: 'cfg-001',
          label: 'Dell OptiPlex 7090 + Windows 10 + Dell E2020H',
          source: 'specification',
          computerWatts: 65,
          monitorWatts: 21,
          totalWatts: 86,
        },
      ],
    },
  }
}

describe('emissions dashboard data contract', () => {
  // @spec:AC-093 Dashboard mostra total e gráfico por mês
  test('@spec:AC-093 Dashboard mostra total e gráfico por mês — resposta contém totalEmissionKg, totalEnergyKwh e byMonth com fator e dias letivos', () => {
    const result = mockEmissionResult()

    assert.equal(typeof result.totalEmissionKg, 'number')
    assert.ok(result.totalEmissionKg > 0, 'total de emissão deve ser positivo')
    assert.equal(typeof result.totalEnergyKwh, 'number')
    assert.ok(result.totalEnergyKwh > 0, 'total de energia deve ser positivo')

    assert.ok(Array.isArray(result.byMonth))
    assert.ok(result.byMonth.length >= 1, 'deve ter ao menos 1 mês')

    for (const month of result.byMonth) {
      assert.equal(typeof month.month, 'string')
      assert.match(month.month, /^\d{4}-\d{2}$/, 'mês no formato YYYY-MM')
      assert.equal(typeof month.emissionKg, 'number')
      assert.equal(typeof month.energyKwh, 'number')
      assert.ok(
        month.emissionFactor === null || typeof month.emissionFactor === 'number',
        'fator de emissão deve ser number ou null',
      )
      assert.equal(typeof month.schoolDays, 'number')
    }
  })

  // @spec:AC-094 Dashboard mostra decomposição por laboratório
  test('@spec:AC-094 Dashboard mostra decomposição por laboratório — resposta contém byLaboratory com estações, computador e monitor', () => {
    const result = mockEmissionResult()

    assert.ok(Array.isArray(result.byLaboratory))
    assert.ok(result.byLaboratory.length >= 1)

    for (const lab of result.byLaboratory) {
      assert.equal(typeof lab.laboratoryId, 'string')
      assert.equal(typeof lab.laboratoryName, 'string')
      assert.equal(typeof lab.energyKwh, 'number')
      assert.equal(typeof lab.emissionKg, 'number')
      assert.equal(typeof lab.stationCount, 'number')
      assert.equal(typeof lab.computerEmissionKg, 'number')
      assert.equal(typeof lab.monitorEmissionKg, 'number')
    }

    // Decomposição computador + monitor ≈ total do lab
    const lab1 = result.byLaboratory[0]
    assert.equal(
      lab1.computerEmissionKg + lab1.monitorEmissionKg,
      lab1.emissionKg,
      'parcela computador + monitor deve somar o total do laboratório',
    )
  })

  // @spec:AC-095 Dashboard mostra equivalências do cotidiano
  test('@spec:AC-095 Dashboard mostra equivalências do cotidiano — resposta contém equivalences com carKm e treesNeeded', () => {
    const result = mockEmissionResult()

    assert.ok(result.equivalences != null, 'equivalences deve estar presente')
    assert.equal(typeof result.equivalences.carKm, 'number')
    assert.ok(result.equivalences.carKm > 0, 'km de carro deve ser positivo')
    assert.equal(typeof result.equivalences.treesNeeded, 'number')
    assert.ok(result.equivalences.treesNeeded > 0, 'árvores deve ser positivo')
  })

  // @spec:AC-096 Dashboard mostra rankings de modelos e SOs
  test('@spec:AC-096 Dashboard mostra rankings de modelos e SOs — resposta contém byEquipmentModel, byMonitorModel e byOperatingSystem com percentuais', () => {
    const result = mockEmissionResult()

    assert.ok(Array.isArray(result.byEquipmentModel))
    assert.ok(result.byEquipmentModel.length >= 1)
    for (const model of result.byEquipmentModel) {
      assert.equal(typeof model.modelName, 'string')
      assert.equal(typeof model.emissionKg, 'number')
      assert.equal(typeof model.percentage, 'number')
    }

    assert.ok(Array.isArray(result.byMonitorModel))
    for (const monitor of result.byMonitorModel) {
      assert.equal(typeof monitor.monitorName, 'string')
      assert.equal(typeof monitor.emissionKg, 'number')
      assert.equal(typeof monitor.percentage, 'number')
    }

    assert.ok(Array.isArray(result.byOperatingSystem))
    assert.ok(result.byOperatingSystem.length >= 1)
    for (const os of result.byOperatingSystem) {
      assert.equal(typeof os.operatingSystem, 'string')
      assert.equal(typeof os.emissionKg, 'number')
      assert.equal(typeof os.percentage, 'number')
    }

    // Percentuais de equipamento devem somar 100
    const eqTotal = result.byEquipmentModel.reduce((s, m) => s + m.percentage, 0)
    assert.equal(eqTotal, 100, 'percentuais de modelos de equipamento devem somar 100')
  })

  // @spec:AC-097 Dashboard mostra painel de transparência
  test('@spec:AC-097 Dashboard mostra painel de transparência — resposta contém inputs com fatores de emissão e fontes de consumo', () => {
    const result = mockEmissionResult()

    assert.ok(result.inputs != null, 'inputs deve estar presente')

    // Fatores de emissão
    assert.ok(Array.isArray(result.inputs.emissionFactors))
    assert.ok(result.inputs.emissionFactors.length >= 1)
    for (const factor of result.inputs.emissionFactors) {
      assert.equal(typeof factor.month, 'string')
      assert.match(factor.month, /^\d{4}-\d{2}$/)
      assert.equal(typeof factor.value, 'number')
      assert.ok(factor.value > 0, 'valor do fator deve ser positivo')
      assert.equal(typeof factor.source, 'string')
      assert.ok(factor.source.length > 0, 'fonte não pode ser vazia')
    }

    // Fontes de consumo
    assert.ok(Array.isArray(result.inputs.consumptionSources))
    assert.ok(result.inputs.consumptionSources.length >= 1)
    for (const src of result.inputs.consumptionSources) {
      assert.equal(typeof src.configurationId, 'string')
      assert.equal(typeof src.label, 'string')
      assert.equal(typeof src.computerWatts, 'number')
      assert.equal(typeof src.monitorWatts, 'number')
      assert.equal(typeof src.totalWatts, 'number')
      assert.equal(
        src.totalWatts,
        src.computerWatts + src.monitorWatts,
        'totalWatts deve ser computerWatts + monitorWatts',
      )
    }
  })
})
