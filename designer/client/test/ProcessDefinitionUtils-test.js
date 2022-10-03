import * as ProcessDefinitionUtils from "../common/ProcessDefinitionUtils";
import {flatMap, without} from "lodash";

describe("getComponentGroupsInCategory", () => {

  let baseComponentGroup
  let enricherComponentGroup
  let filterComponent
  let splitComponent
  let accountServiceComponent
  let clientHttpServiceComponent

  beforeAll(() => {
    baseComponentGroup = processDefinition.componentGroups.find(group => group.name === "base")
    enricherComponentGroup = processDefinition.componentGroups.find(group => group.name === "enrichers")
    filterComponent = baseComponentGroup.components.find(n => n.type === "filter")
    splitComponent = baseComponentGroup.components.find(n => n.type === "split")
    accountServiceComponent = enricherComponentGroup.components.find(n => n.node.service.id === "accountService")
    clientHttpServiceComponent = enricherComponentGroup.components.find(n => n.node.service.id === "clientHttpService")
  })

  it("should return all nodes available in category", () => {
    const componentGroups = ProcessDefinitionUtils.getCategoryComponentGroups(processDefinition, "Category1")

    expect(componentGroups).toEqual(processDefinition.componentGroups)
  })

  it("should filter out unavailable nodes in category", () => {
    const componentGroups = ProcessDefinitionUtils.getCategoryComponentGroups(processDefinition, "Category2")

    expect(componentGroups).toEqual([
      {name: "base", components: [filterComponent, splitComponent]},
      {name: "enrichers", components: [clientHttpServiceComponent]},
    ])
  })

  it("should leave empty group if no nodes are available", () => {
    const componentGroups = ProcessDefinitionUtils.getCategoryComponentGroups(processDefinition, "Technical")

    expect(componentGroups).toEqual([
      {name: "base", components: [filterComponent, splitComponent]},
      {name: "enrichers", components: []},
    ])
  })
})

describe("getFlatCategoryComponentGroups", () => {
  let allFlatNodes

  beforeAll(() => {
    allFlatNodes = flatMap(processDefinition.componentGroups, group => group.components)
  })

  it("should return all flat nodes available in category", () => {
    const flatNodes = ProcessDefinitionUtils.getFlatCategoryComponents(processDefinition, "Category1")

    expect(flatNodes).toEqual(allFlatNodes)
  })

  it("should filter out unavailable nodes in category", () => {
    const flatNodes = ProcessDefinitionUtils.getFlatCategoryComponents(processDefinition, "Category2")

    expect(flatNodes).toEqual(without(allFlatNodes, allFlatNodes[2]))
  })
})

const processDefinition = {
  componentGroups: [
    {
      "name": "base",
      "components": [
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
            "RequestResponseCategory1",
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
            "RequestResponseCategory1",
            "Technical",
            "Category1"
          ],
          "branchParametersTemplate": []
        }
      ]
    },
    {
      "name": "enrichers",
      "components": [
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
