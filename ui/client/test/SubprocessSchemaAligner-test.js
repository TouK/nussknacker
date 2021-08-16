import * as SubprocessSchemaAligner from '../components/graph/SubprocessSchemaAligner'
import _ from 'lodash'

const subprocessProcessDefinitionData = {
  nodesToAdd: [
    {
      name: "fragments",
      possibleNodes: [
        {
          type: "subprocess", label: "subproc1", node: {
          type: "SubprocessInput", id: "", ref: {
            id: "subproc1",
            parameters: [
              {"name": "param1", "expression": {"language": "spel", "expression": "''"}},
              {"name": "param2", "expression": {"language": "spel", "expression": "''"}}
            ]
          }
        }
        }
      ]
    }
  ],
  processDefinition: {}
}

describe("subprocess schema aligner test", () => {
  it("should remove redundant and add missing parameters according to schema", () => {
    const subprocessNode = {
      type: "SubprocessInput", id: "node4",
      ref: {
        id: "subproc1",
        parameters: [
          {name: "oldParam1", expression: {language: "spel", expression: "'abc'"}},
          {name: "param2", expression: {language: "spel", expression: "'cde'"}}
        ]
      },
      outputs : {}
    }

    const alignedSubprocess = SubprocessSchemaAligner.alignSubprocessWithSchema(subprocessProcessDefinitionData, subprocessNode)

    expect(alignedSubprocess.ref.parameters).toEqual([
      { name: "param1", expression: { language: "spel", expression: "''"}},
      { name: "param2", expression: { language: "spel", expression: "'cde'"}}
    ])
    expect(_.omit(alignedSubprocess, 'ref')).toEqual(_.omit(subprocessNode, 'ref'))
  })

  it("should not change anything if subprocess is valid with schema", () => {
    const subprocessNode = {
      type: "SubprocessInput", id: "node4",
      ref: {
        id: "subproc1",
        parameters: [
          {name: "param1", expression: {language: "spel", expression: "'abc'"}},
          {name: "param2", expression: {language: "spel", expression: "'cde'"}}
        ]
      },
      outputs : {}
    }

    const alignedSubprocess = SubprocessSchemaAligner.alignSubprocessWithSchema(subprocessProcessDefinitionData, subprocessNode)

    expect(alignedSubprocess).toEqual(subprocessNode)
  })
})