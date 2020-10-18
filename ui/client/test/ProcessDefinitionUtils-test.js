import * as ProcessDefinitionUtils from "../common/ProcessDefinitionUtils";
import {flatMap, without} from "lodash";

describe("getNodesToAddInCategory", () => {

  let baseNodes
  let enricherNodes
  let filterNode
  let splitNode
  let accountServiceNode
  let clientHttpServiceNode

  beforeAll(() => {
    baseNodes = processDefinition.nodesToAdd.find(nodes => nodes.name === "base")
    enricherNodes = processDefinition.nodesToAdd.find(nodes => nodes.name === "enrichers")
    filterNode = baseNodes.possibleNodes.find(n => n.type === "filter")
    splitNode = baseNodes.possibleNodes.find(n => n.type === "split")
    accountServiceNode = enricherNodes.possibleNodes.find(n => n.node.service.id === "accountService")
    clientHttpServiceNode = enricherNodes.possibleNodes.find(n => n.node.service.id === "clientHttpService")
  })

  it("should return all nodes available in category", () => {
    const nodesToAdd = ProcessDefinitionUtils.getNodesToAddInCategory(processDefinition, "Category1")

    expect(nodesToAdd).toEqual(processDefinition.nodesToAdd)
  })

  it("should filter out unavailable nodes in category", () => {
    const nodesToAdd = ProcessDefinitionUtils.getNodesToAddInCategory(processDefinition, "Category2")

    expect(nodesToAdd).toEqual([
      {name: "base", possibleNodes: [filterNode, splitNode]},
      {name: "enrichers", possibleNodes: [clientHttpServiceNode]},
    ])
  })

  it("should leave empty group if no nodes are available", () => {
    const nodesToAdd = ProcessDefinitionUtils.getNodesToAddInCategory(processDefinition, "Technical")

    expect(nodesToAdd).toEqual([
      {name: "base", possibleNodes: [filterNode, splitNode]},
      {name: "enrichers", possibleNodes: []},
    ])
  })
})

describe("getFlatNodesToAddInCategory", () => {
  let allFlatNodes

  beforeAll(() => {
    allFlatNodes = flatMap(processDefinition.nodesToAdd, group => group.possibleNodes)
  })

  it("should return all flat nodes available in category", () => {
    const flatNodes = ProcessDefinitionUtils.getFlatNodesToAddInCategory(processDefinition, "Category1")

    expect(flatNodes).toEqual(allFlatNodes)
  })

  it("should filter out unavailable nodes in category", () => {
    const flatNodes = ProcessDefinitionUtils.getFlatNodesToAddInCategory(processDefinition, "Category2")

    expect(flatNodes).toEqual(without(allFlatNodes, allFlatNodes[2]))
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
