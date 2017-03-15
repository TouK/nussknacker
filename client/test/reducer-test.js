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
        ids: ["node0", "looo"]
      }
    })
    expect(NodeUtils.getAllGroups(result.graphReducer.processToDisplay)).toEqual(
      [{
        id: "abcde",
        nodes: ["node0", "looo"]
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
          id: "node3"
        }
      },
      {
        type: "DISPLAY_NODE_DETAILS",
        nodeToDisplay: {
          id: "node2"
        }
      },
      {
        type: "FINISH_GROUPING"
      }])

    expect(NodeUtils.getAllGroups(result.graphReducer.processToDisplay)).toEqual(
      [{
        id: "acdc",
        nodes: ["node0", "looo"]
      },{
        id: "node3-node2",
        nodes: ["node3", "node2"]
      }
      ]
    )

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
              "node0",
              "looo"
            ]
          }
        ]
      }
    },
    "nodes": [
      {
        "type": "Source",
        "id": "node0",
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
        "id": "looo",
        "expression": {
          "language": "spel",
          "expression": "4 / (#input.length -5) >= 0"
        }
      },
      {
        "type": "Enricher",
        "id": "node3",
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
        "id": "node2",
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
        "from": "node0",
        "to": "looo"
      },
      {
        "from": "looo",
        "to": "node3",
        "edgeType": {
          "type": "FilterTrue"
        }
      },
      {
        "from": "node3",
        "to": "node2"
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