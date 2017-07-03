import NodeParametersMerger from '../components/graph/NodeParametersMerger'
import _ from 'lodash'

const processDefinitionData = {
  nodesToAdd: [
    {
      name: "enrichers", possibleNodes: [
        {
          type: "enricher", label: "multipleParamsService", node: {
          type: "Enricher", id: "", service: {
              id: "multipleParamsService", parameters: [
                { "name": "foo", "expression": { "language": "spel", "expression": "''"}},
                { "name": "bar", "expression": { "language": "spel", "expression": "''"}}
              ]
            },
            output: "output"
          }
        }
      ],
    },
    {
      name: "custom", possibleNodes: [
        {
          type: "customNode", label: "stateful",
          node: { type: "CustomNode", id: "", outputVar: "outputVar", nodeType: "stateful",
            parameters: [
              { "name": "keyBy", "expression": { "language": "spel", "expression": "''"}}
            ]
          }
        }
      ]
    },
    {
      name: "sinks", possibleNodes: [
        {
          type: "sink", label: "kafka-string", node: {
          type: "Sink", id: "", ref: {
            typ: "kafka-string",
              parameters: [{ "name": "topic", "value": "TODO"}]
          },
          endResult: { "language": "spel", "expression": "#input" }
          }
        }
      ]
    }
  ],

  processDefinition: {
    exceptionHandlerFactory: {
      parameters: [{name: "someParam", typ: {refClazzName: "java.lang.String"}}],
      returnType: { refClazzName: "java.lang.Object"},
      categories: []
    }
  }
}

describe("node parameters merger test", () => {
  it("should add missing properties parameter that is present in definition", () => {
    const properties = { exceptionHandler : { parameters: [] } }

    const propertiesWithAddedParam = NodeParametersMerger.addMissingParametersToNode(processDefinitionData, properties)

    expect(propertiesWithAddedParam).toEqual({ exceptionHandler : { parameters: [{name: "someParam", value : ""}] } })
  })

  it("should add missing enricher parameter that is present in definition", () => {
    const enricherNode = {
      type: "Enricher", id: "node1",
      service: {
        id: "multipleParamsService",
        parameters: [
          { name: "bar", expression: { language: "spel", expression: "'a'"}}
        ]
      },
      output: "output"
    }

    const enricherWithAddedParam = NodeParametersMerger.addMissingParametersToNode(processDefinitionData, enricherNode)

    expect(enricherWithAddedParam.service.parameters).toEqual([
      { name: "foo", expression: { language: "spel", expression: "''"}},
      { name: "bar", expression: { language: "spel", expression: "'a'"}}
    ])
    expect(_.omit(enricherWithAddedParam, 'service')).toEqual(_.omit(enricherNode, 'service'))
  })

  it("should do nothing if all parameters are present", () => {
    const enricherNode = {
      type: "Enricher", id: "node1",
      service: {
        id: "multipleParamsService",
        parameters: [
          { name: "foo", expression: { language: "spel", expression: "''"}},
          { name: "bar", expression: { language: "spel", expression: "'a'"}}
        ]
      },
      output: "output"
    }

    const enricherWithAddedParam = NodeParametersMerger.addMissingParametersToNode(processDefinitionData, enricherNode)

    expect(enricherWithAddedParam).toEqual(enricherNode)
  })

  it("should add missing custom node parameter that is present in definition", () => {
    const customNode = {
      type: "CustomNode", id: "node3", outputVar: "outputVar", nodeType: "stateful",
      parameters: []
    }

    const customNodeWithAddedParam = NodeParametersMerger.addMissingParametersToNode(processDefinitionData, customNode)

    expect(customNodeWithAddedParam.parameters).toEqual([
      { "name": "keyBy", "expression": { "language": "spel", "expression": "''"}}
    ])
    expect(_.omit(customNodeWithAddedParam, 'parameters')).toEqual(_.omit(customNode, 'parameters'))
  })

  it("should add missing sink parameter that is present in definition", () => {
    const sinkNode = {
      type: "Sink", id: "node2", ref: {
        typ: "kafka-string",
        parameters: []
      },
      endResult: { "language": "spel", "expression": "#input"}
    }

    const sinkNodeWithAddedParam = NodeParametersMerger.addMissingParametersToNode(processDefinitionData, sinkNode)

    expect(sinkNodeWithAddedParam.ref.parameters).toEqual([
      { "name": "topic", "value": "TODO"}
    ])
    expect(_.omit(sinkNodeWithAddedParam, 'ref')).toEqual(_.omit(sinkNode, 'ref'))
  })

  it("should not throw for service not present in definition", () => {
    const enricherNode = {
      type: "Enricher", id: "node1", service: { id: "doesNotExistId", parameters: []}, output: "output"
    }

    const enricherWithAddedParam = NodeParametersMerger.addMissingParametersToNode(processDefinitionData, enricherNode)

    expect(enricherWithAddedParam).toEqual(enricherNode)
  })

})