# RFC 002 — Fontes de Dados de Energia para Cálculo de Emissões

| Campo           | Valor                        |
| --------------- | ---------------------------- |
| Driver          | @andreydedey                 |
| Status          | Rascunho                     |
| Criado em       | 2026-09-30                   |
| Impacto         | MÉDIO                        |

## Contexto

O `EmissionCalculationService` atualmente calcula o consumo de energia de cada
configuração de equipamento a partir das especificações técnicas (TDP do
processador, GPU e monitor). Essa abordagem é chamada internamente de
**"specification"** e é a única fonte de dados implementada.

No entanto, algumas instituições podem ter acesso a **medições reais de
consumo** (smart meters, medidores de tomada, dados da concessionária). Usar
dados medidos aumentaria a precisão do cálculo, e o sistema precisa suportar
ambas as fontes sem duplicar a lógica de cálculo.

## Problema

O serviço de cálculo está acoplado à fonte "specification":

- O consumo em watts é extraído diretamente de `EquipmentModel.tdpWatts`,
  `gpuTdpWatts` e `Monitor.watts`
- Não existe abstração que permita trocar a fonte de dados sem alterar o
  serviço inteiro
- Adicionar uma nova fonte (medição) exigiria `if/else` espalhados pelo
  método `calculate()`, violando o princípio aberto-fechado

## Critérios de Decisão

| Critério                          | Peso |
| --------------------------------- | ---- |
| Facilidade de adicionar nova fonte| Alta |
| Mínimo impacto no código existente| Alta |
| Testabilidade isolada             | Média|
| Complexidade de implementação     | Média|

## Opção 1 — Strategy Pattern (`EnergyDataSource`)

Extrair uma interface `EnergyDataSource` que o `EmissionCalculationService`
recebe como dependência. Cada implementação encapsula como obter o consumo em
watts para uma dada configuração.

```
interface EnergyDataSource {
    ConsumptionData getConsumption(Configuration config)
}

record ConsumptionData(int computerWatts, int monitorWatts, String source)
```

Implementações:
- `SpecificationEnergyDataSource` — extrai watts do TDP (comportamento atual)
- `MeteringEnergyDataSource` — busca dados de medição por configuração/lab

O `EmissionCalculationService` chama `dataSource.getConsumption(config)` ao invés
de acessar `model.getTdpWatts()` diretamente. A fonte usada pode ser configurada
por instituição ou por laboratório.

**Prós:**
- Desacopla completamente o cálculo da fonte de dados
- Cada fonte é testável isoladamente
- Adicionar uma terceira fonte (ex: benchmark) não altera o service
- Compatível com injeção de dependência do Spring

**Contras:**
- Requer refatoração do método `calculate()` para delegar ao strategy
- Pequena complexidade adicional na resolução de qual strategy usar

## Opção 2 — Não fazer nada (manter como está)

Manter o acoplamento direto com TDP e só refatorar quando a medição for
realmente necessária.

**Prós:**
- Zero esforço agora
- Código mais simples enquanto há uma única fonte

**Contras:**
- Cada nova fonte exigirá refatoração retroativa mais custosa
- Testes do serviço de cálculo ficam acoplados à estrutura das entidades

## Recomendação

**Opção 1** — implementar o Strategy Pattern quando a funcionalidade de medição
for priorizada. A interface `EnergyDataSource` deve ser criada junto com o
primeiro caso de uso real de medição, evitando abstração prematura.

Enquanto isso, o código atual (Opção 2) é aceitável. A recomendação é preparar
a migração documentando o contrato da interface agora, e implementar quando
houver demanda.

## Próximos Passos

- [ ] Validar se alguma instituição tem dados de medição disponíveis
- [ ] Definir o modelo de dados para armazenar medições
- [ ] Implementar `EnergyDataSource` interface + `SpecificationEnergyDataSource`
- [ ] Refatorar `EmissionCalculationService.calculate()` para usar a interface
