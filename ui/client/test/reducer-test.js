import reducer from "../reducers/index"
import NodeUtils from "../components/graph/NodeUtils"

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
    "test",
  ],
  "json": {
    "id": "DEFGH",
    "properties": {
      "parallelism": 3,
      "exceptionHandler": {
        "parameters": [
          {
            "name": "param1",
            "value": "adef",
          },
        ],
      },
      "additionalFields": {
        "groups": [
          {
            "id": "acdc",
            "nodes": [
              "kafka-transaction",
              "filter",
            ],
          },
        ],
      },
    },
    "nodes": [
      {
        "type": "Source",
        "id": "kafka-transaction",
        "ref": {
          "typ": "kafka-transaction",
          "parameters": [],
        },
        "additionalFields": {
          "description": "asdfasdfłóóódźźźasdfsdfasdfasdfasdfasdf",
        },
      },
      {
        "type": "Filter",
        "id": "filter",
        "expression": {
          "language": "spel",
          "expression": "4 / (#input.length -5) >= 0",
        },
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
                "expression": "'3434'",
              },
            },
          ],
        },
        "output": "output",
      },
      {
        "type": "Sink",
        "id": "sendSms",
        "ref": {
          "typ": "sendSms",
          "parameters": [],
        },
      },
    ],
    "edges": [
      {
        "from": "kafka-transaction",
        "to": "filter",
      },
      {
        "from": "filter",
        "to": "paramService",
        "edgeType": {
          "type": "FilterTrue",
        },
      },
      {
        "from": "paramService",
        "to": "sendSms",
      },
    ],
    "validationResult": {
      "errors": {
        "invalidNodes": {},
        "processPropertiesErrors": [],
        "globalErrors": [],
      },
    },
  },
}

const baseState = reducer({}, {
  type: "@@INIT",
})

const baseStateWithProcess = reducer(baseState, {
  type: "DISPLAY_PROCESS",
  fetchedProcessDetails: baseProcessState,
})

const reduceAll = (actions) => actions.reduce((state, action) => reducer(state, action), baseStateWithProcess)

describe("Reducer suite", () => {
  it("Display process", () => {
    expect(baseStateWithProcess.graphReducer.processToDisplay.id).toEqual(baseProcessState.id)
  })

})

const testNode = {
  "type": "Enricher",
  "id": "Enricher ID",
  "service": {
    "id": "paramService",
    "parameters": [
      {
        "name": "param",
        "expression": {
          "language": "spel",
          "expression": "'3434'",
        },
      },
    ],
  },
  "output": "output",
}

const testPosition = {x: 10, y: 20}

describe("Nodes added", () => {
  it("should add single node", () => {
    const result = reduceAll([{
      type: "NODE_ADDED",
      node: testNode,
      position: testPosition,
    }])

    expect(NodeUtils.getNodeById(testNode.id, result.graphReducer.processToDisplay)).toEqual(testNode)
    expect(result.graphReducer.layout.find(n => n.id === testNode.id).position).toEqual(testPosition)
  })

  it("should add single node with unique id", () => {
    const result = reduceAll([{
      type: "NODE_ADDED",
      node: {...testNode, id: "kafka-transaction"},
      position: testPosition,
    }])

    expect(NodeUtils.getNodeById("kafka-transaction 1", result.graphReducer.processToDisplay)).toEqual({
      ...testNode,
      id: "kafka-transaction 1",
    })
    expect(result.graphReducer.layout.find(n => n.id).position).toEqual(testPosition)
  })

  it("should add multiple nodes with unique id", () => {
    const result = reduceAll([{
      type: "NODES_WITH_EDGES_ADDED",
      nodesWithPositions: [
        {
          node: {...testNode, id: "kafka-transaction"},
          position: testPosition,
        },
        {
          node: {...testNode, id: "kafka-transaction"},
          position: testPosition,
        },
      ],
      edges: [],
    }])

    expect(NodeUtils.getNodeById("kafka-transaction (copy 1)", result.graphReducer.processToDisplay)).toEqual({
      ...testNode,
      id: "kafka-transaction (copy 1)",
    })
    expect(NodeUtils.getNodeById("kafka-transaction (copy 2)", result.graphReducer.processToDisplay)).toEqual({
      ...testNode,
      id: "kafka-transaction (copy 2)",
    })
  })

  it("should add nodes with edges", () => {
    const result = reduceAll([{
      type: "NODES_WITH_EDGES_ADDED",
      nodesWithPositions: [
        {
          node: {...testNode, id: "newNode"},
          position: testPosition,
        },
        {
          node: {...testNode, id: "kafka-transaction"},
          position: testPosition,
        },
      ],
      edges: [
        {from: "newNode", to: "kafka-transaction"},
      ],
      processDefinitionData: {
        edgesForNodes: [],
      },
    }])

    expect(NodeUtils.getNodeById("newNode", result.graphReducer.processToDisplay)).toEqual({...testNode, id: "newNode"})
    expect(NodeUtils.getNodeById("kafka-transaction (copy 1)", result.graphReducer.processToDisplay)).toEqual({
      ...testNode,
      id: "kafka-transaction (copy 1)",
    })
    expect(NodeUtils.getEdgeById("newNode-kafka-transaction (copy 1)", result.graphReducer.processToDisplay)).toEqual({
      from: "newNode",
      to: "kafka-transaction (copy 1)",
    })
  })
})
