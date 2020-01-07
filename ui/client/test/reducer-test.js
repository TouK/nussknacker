import defaultReducer from '../reducers/index'
import NodeUtils from '../components/graph/NodeUtils'

import _ from 'lodash'


describe("Reducer suite", () => {
  it("Display process", () => {
    const result = baseReducerWithProcess()
    expect(result.graphReducer.processToDisplay.id).toEqual(baseProcessState.id)
  })

  it("Should change group id", () => {
    const result = defaultReducer(baseReducerWithProcess(), {
      type: "EDIT_GROUP",
      oldGroupId: "acdc",
      newGroup: {
        id: "abcde",
        ids: ["kafka-transaction", "filter"]
      }
    })
    expect(NodeUtils.getAllGroups(result.graphReducer.processToDisplay)).toEqual(
      [{
        id: "abcde",
        nodes: ["kafka-transaction", "filter"]
      }]
    )
  })

  it("Should be able to add new group", () => {
    const result = reduceAll([
      {
        type: "START_GROUPING"
      },
      {
        type: "DISPLAY_NODE_DETAILS",
        nodeToDisplay: {
          id: "paramService"
        }
      },
      {
        type: "DISPLAY_NODE_DETAILS",
        nodeToDisplay: {
          id: "sendSms"
        }
      },
      {
        type: "FINISH_GROUPING"
      }])

    expect(NodeUtils.getAllGroups(result.graphReducer.processToDisplay)).toEqual(
      [{
        id: "acdc",
        nodes: ["kafka-transaction", "filter"]
      },{
        id: "paramService-sendSms",
        nodes: ["paramService", "sendSms"]
      }
      ]
    )

  })

})

describe("Nodes added", () => {

  let uniqueId
  let node
  let position

  beforeEach(() => {
    uniqueId = "unique id"
    node = {
      "type": "Enricher",
      "id": uniqueId,
      "service": {
        "id": "paramService",
        "parameters": [
          {
            "name": "param",
            "expression": {
              "language": "spel",
              "expression": "'3434'"
            }
          }
        ]
      },
      "output": "output"
    }
    position = {x: 10, y: 20}
  })

  it("should add single node with unique id", () => {
    const result = reduceAll([
      {
        type: "NODE_ADDED",
        node,
        position
      }
    ])

    expect(NodeUtils.getNodeById(uniqueId, result.graphReducer.processToDisplay)).toEqual(node)
    expect(_.find(result.graphReducer.layout, n => n.id === uniqueId).position).toEqual(position)
  })

  it("should add single node with id 1 bigger", () => {
    const result = reduceAll([
      {
        type: "NODE_ADDED",
        node: {...node, id: "kafka-transaction"},
        position
      }
    ])

    expect(NodeUtils.getNodeById("kafka-transaction 1", result.graphReducer.processToDisplay)).toEqual({...node, id: "kafka-transaction 1"})
    expect(_.find(result.graphReducer.layout, n => n.id).position).toEqual(position)
  })

  it("should add multiple nodes with copy suffix", () => {
    const result = reduceAll([{
      type: "NODES_WITH_EDGES_ADDED",
      nodesWithPositions: [
        {
          node: {...node, id: "kafka-transaction"},
          position
        },
        {
          node: {...node, id: "kafka-transaction"},
          position
        }
      ],
      edges: []
    }])

    expect(NodeUtils.getNodeById("kafka-transaction (copy 1)", result.graphReducer.processToDisplay)).toEqual({...node, id: "kafka-transaction (copy 1)"})
    expect(NodeUtils.getNodeById("kafka-transaction (copy 2)", result.graphReducer.processToDisplay)).toEqual({...node, id: "kafka-transaction (copy 2)"})
  })

  it("should add nodes with edges", () => {
    const result = reduceAll([{
      type: "NODES_WITH_EDGES_ADDED",
      nodesWithPositions: [
        {
          node: {...node, id: "newNode"},
          position
        },
        {
          node: {...node, id: "kafka-transaction"},
          position
        }
      ],
      edges: [
        {from: "newNode", to: "kafka-transaction"}
      ],
      processDefinitionData: {
        edgesForNodes: []
      }
    }])

    expect(NodeUtils.getNodeById("newNode", result.graphReducer.processToDisplay)).toEqual({...node, id: "newNode"})
    expect(NodeUtils.getNodeById("kafka-transaction (copy 1)", result.graphReducer.processToDisplay)).toEqual({...node, id: "kafka-transaction (copy 1)"})
    expect(NodeUtils.getEdgeById("newNode-kafka-transaction (copy 1)", result.graphReducer.processToDisplay)).toEqual({from: "newNode", to: "kafka-transaction (copy 1)"})
  })
})

const reduceAll = (actions) => _.reduce(actions, (state, action) => defaultReducer(state, action), baseReducerWithProcess())


const baseReducerWithProcess = () => defaultReducer({}, {
  type: "DISPLAY_PROCESS",
  fetchedProcessDetails: baseProcessState
})

const baseProcessState = {
  "id": "DEFGH",
  "name": "DEFGH",
  "processVersionId": 23,
  "isLatestVersion": true,
  "processType": "graph",
  "processCategory": "Category1",
  "modificationDate": "2017-02-14T11:16:56.686",
  "createdAt": "2017-02-14T11:16:56.686",
  "createdBy": "admin",
  "tags": [],
  "currentlyDeployedAt": [
    "test"
  ],
  "json": {
    "id": "DEFGH",
    "properties": {
      "parallelism": 3,
      "exceptionHandler": {
        "parameters": [
          {
            "name": "param1",
            "value": "adef"
          }
        ]
      },
      "additionalFields": {
        "groups": [
          {
            "id": "acdc",
            "nodes": [
              "kafka-transaction",
              "filter"
            ]
          }
        ]
      }
    },
    "nodes": [
      {
        "type": "Source",
        "id": "kafka-transaction",
        "ref": {
          "typ": "kafka-transaction",
          "parameters": []
        },
        "additionalFields": {
          "description": "asdfasdfłóóódźźźasdfsdfasdfasdfasdfasdf"
        }
      },
      {
        "type": "Filter",
        "id": "filter",
        "expression": {
          "language": "spel",
          "expression": "4 / (#input.length -5) >= 0"
        }
      },
      {
        "type": "Enricher",
        "id": "paramService",
        "service": {
          "id": "paramService",
          "parameters": [
            {
              "name": "param",
              "expression": {
                "language": "spel",
                "expression": "'3434'"
              }
            }
          ]
        },
        "output": "output"
      },
      {
        "type": "Sink",
        "id": "sendSms",
        "ref": {
          "typ": "sendSms",
          "parameters": []
        },
        "endResult": {
          "language": "spel",
          "expression": "#input"
        }
      }
    ],
    "edges": [
      {
        "from": "kafka-transaction",
        "to": "filter"
      },
      {
        "from": "filter",
        "to": "paramService",
        "edgeType": {
          "type": "FilterTrue"
        }
      },
      {
        "from": "paramService",
        "to": "sendSms"
      }
    ],
    "validationResult": {
      "errors" : {
        "invalidNodes": {},
        "processPropertiesErrors": [],
        "globalErrors": []
      }
    }
  },
  "history": []
}