import ProcessCountsUtil from '../common/ProcessCountsUtil'

describe("process counts", () => {

  it("should find counts for groups", () => {
    const processCountsWithoutGroups = {
      "node1": { "all": 2, "errors": 0},
      "node2": { "all": 2, "errors": 0},
      "node3": { "all": 2, "errors": 0}
    }
    const nodesWithGroups = [
      { "id": "node3"},
      {
        "id": "node1-node2",
        "type": "_group",
        "nodes": [{ "id": "node1",}, { "id": "node2",}],
        "ids": ["node1", "node2"]
      }
    ]

    expect(ProcessCountsUtil.processCounts(processCountsWithoutGroups, nodesWithGroups)).toEqual({
      "node1-node2": { "all": 2, "errors": 0},
      "node3": { "all": 2, "errors": 0},
      "node1": { "all": 2, "errors": 0},
      "node2": { "all": 2, "errors": 0}
    })
  })

  it("should find counts for test results", () => {
    const testResults = {
      "mockedResults": {},
      "invocationResults": {},
      "nodeResults": {
        "node3": [{"context": {}}, { "context": {}}],
        "node1": [{ "context": {}}, { "context": {}}],
        "node2": [{ "context": {}}, { "context": {}}]
      }
    }
    const nodesWithGroups = [
      { "id": "node3"},
      {
        "id": "node1-node2",
        "type": "_group",
        "nodes": [{ "id": "node1",}, { "id": "node2",}],
        "ids": ["node1", "node2"]
      }
    ]
    
    expect(ProcessCountsUtil.processCountsForTests(testResults, nodesWithGroups)).toEqual({
      "node1-node2": { "all": 2, "errors": 0},
      "node3": { "all": 2, "errors": 0},
      "node1": { "all": 2, "errors": 0},
      "node2": { "all": 2, "errors": 0}
    })
  })

})
