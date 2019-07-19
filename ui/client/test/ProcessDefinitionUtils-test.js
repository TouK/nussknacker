import * as ProcessDefinitionUtils from "../common/ProcessDefinitionUtils";

describe("getNodesToAddInCategory", () => {
  it("should return all nodes available in category", () => {
    const nodesToAdd = ProcessDefinitionUtils.getNodesToAddInCategory(processDefinition, "Category1")

    expect(nodesToAdd).toEqual(processDefinition.nodesToAdd)
  })

  it("should filter out unavailable nodes in category", () => {
    const nodesToAdd = ProcessDefinitionUtils.getNodesToAddInCategory(processDefinition, "Category2")

    expect(nodesToAdd).toEqual([
      processDefinition.nodesToAdd[0],
      {
        "name": "enrichers",
        "possibleNodes": [processDefinition.nodesToAdd[1].possibleNodes[1]]
      }
    ])
  })

  it("should leave empty group if no nodes are available", () => {
    const nodesToAdd = ProcessDefinitionUtils.getNodesToAddInCategory(processDefinition, "Technical")

    expect(nodesToAdd.length).toEqual(2)
    expect(nodesToAdd[0].name).toEqual("base")
    expect(nodesToAdd[0].possibleNodes.length).toEqual(2)
    expect(nodesToAdd[1].name).toEqual("enrichers")
    expect(nodesToAdd[1].possibleNodes.length).toEqual(0)
  })
})

describe("getFlatNodesToAddInCategory", () => {
  let allFlatNodes

  beforeAll(() => {
    allFlatNodes = _.flatMap(processDefinition.nodesToAdd, group => group.possibleNodes)
  })

  it("should return all flat nodes available in category", () => {
    const flatNodes = ProcessDefinitionUtils.getFlatNodesToAddInCategory(processDefinition, "Category1")

    expect(flatNodes).toEqual(allFlatNodes)
  })

  it("should filter out unavailable nodes in category", () => {
    const flatNodes = ProcessDefinitionUtils.getFlatNodesToAddInCategory(processDefinition, "Category2")

    expect(flatNodes).toEqual([allFlatNodes[0], allFlatNodes[1], allFlatNodes[3]])
  })
})

const processDefinition = {
  nodesToAdd: [
    {
      "name": "base",
      "possibleNodes": [
        {
          "type": "filter",
          "label": "filter",
          "node": {
            "type": "Filter",
            "id": "",
            "expression": {
              "language": "spel",
              "expression": "true"
            }
          },
          "categories": [
            "Category2",
            "Default",
            "StandaloneCategory1",
            "Technical",
            "Category1"
          ],
          "branchParametersTemplate": []
        },
        {
          "type": "split",
          "label": "split",
          "node": {
            "type": "Split",
            "id": ""
          },
          "categories": [
            "Category2",
            "Default",
            "StandaloneCategory1",
            "Technical",
            "Category1"
          ],
          "branchParametersTemplate": []
        }
      ]
    },
    {
      "name": "enrichers",
      "possibleNodes": [
        {
          "type": "enricher",
          "label": "accountService",
          "node": {
            "type": "Enricher",
            "id": "",
            "service": {
              "id": "accountService",
              "parameters": []
            },
            "output": "output"
          },
          "categories": [
            "Category1"
          ],
          "branchParametersTemplate": []
        },
        {
          "type": "enricher",
          "label": "clientHttpService",
          "node": {
            "type": "Enricher",
            "id": "",
            "service": {
              "id": "clientHttpService",
              "parameters": [
                {
                  "name": "id",
                  "expression": {
                    "language": "spel",
                    "expression": "''"
                  }
                }
              ]
            },
            "output": "output"
          },
          "categories": [
            "Category2",
            "Category1"
          ],
          "branchParametersTemplate": []
        }
      ]
    }
  ]
}