import NodeUtils from '../components/graph/NodeUtils'
import _ from 'lodash'


describe("nodes grouped", () => {
  it("should return same process when no groups found", () => {
    const process = createProcess([])
    expect(NodeUtils.nodesFromProcess(process)).toEqual(process.nodes)
  })

  it("should group nodes in line", () => {
    const process = createProcess([["node1", "node2"]])
    expect(NodeUtils.nodesFromProcess(process)).toEqual([
      {id: "node3"},{id: "node4"}, {id: "node5"}, {id: "node6"}, {id: "node7"}, {id: "node8"},
        {id: "node1-node2", type: "_group", nodes: [{id: "node1"}, {id: "node2"}], ids: ["node1", "node2"]}
    ])
  })

  it("should handle two groups", () => {
    const process = createProcess([["node1", "node2"], ["node6", "node8"]])
    expect(NodeUtils.nodesFromProcess(process)).toEqual([
      {id: "node3"},{id: "node4"}, {id: "node5"}, {id: "node7"},
        {id: "node1-node2", type: "_group", nodes: [{id: "node1"}, {id: "node2"}], ids: ["node1", "node2"]},
        {id: "node6-node8", type: "_group", nodes: [{id: "node6"}, {id: "node8"}], ids: ["node6", "node8"]}
    ])
  })

  it("should handle group with split", () => {
    const process = createProcess([["node3", "node4", "node5", "node6"]])
    expect(NodeUtils.nodesFromProcess(process)).toEqual([
      {id: "node1"},{id: "node2"}, {id: "node7"}, {id: "node8"},
      {id: "node3-node4-node5-node6", type: "_group", nodes: [{id: "node3"}, {id: "node4"}, {id: "node5"}, {id: "node6"}], ids: ["node3", "node4", "node5", "node6"]}
    ])
  })

  it("should handle group ending with split", () => {
    const process = createProcess([["node1", "node2", "node3"]])
    expect(NodeUtils.nodesFromProcess(process)).toEqual([
      {id: "node4"},{id: "node5"}, {id: "node6"}, {id: "node7"}, {id: "node8"},
      {id: "node1-node2-node3", type: "_group", nodes: [{id: "node1"}, {id: "node2"}, {id: "node3"}], ids: ["node1", "node2", "node3"]}
    ])
  })

})


describe("edges grouped", () => {
  it("should return same process when no groups found", () => {
    const process = createProcess([])
    expect(NodeUtils.edgesFromProcess(process)).toEqual(process.edges)
  })

  it("should group nodes in line", () => {
    const process = createProcess([["node1", "node2"]])
    expect(NodeUtils.edgesFromProcess(process)).toEqual([
      { "from": "node1-node2", "to": "node3"},
      { "from": "node3", "to": "node4"},
      { "from": "node3", "to": "node5"},
      { "from": "node4", "to": "node6"},
      { "from": "node5", "to": "node7"},
      { "from": "node6", "to": "node8"}
    ])
  })

  it("should handle two groups", () => {
    const process = createProcess([["node1", "node2"], ["node6", "node8"]])
    expect(NodeUtils.edgesFromProcess(process)).toEqual([
      { "from": "node1-node2", "to": "node3"},
      { "from": "node3", "to": "node4"},
      { "from": "node3", "to": "node5"},
      { "from": "node4", "to": "node6-node8"},
      { "from": "node5", "to": "node7"}
    ])
  })

  it("should handle group with split", () => {
    const process = createProcess([["node3", "node4", "node5", "node6"]])
    expect(NodeUtils.edgesFromProcess(process)).toEqual([
      { "from": "node1", "to": "node2"},
      { "from": "node2", "to": "node3-node4-node5-node6"},
      { "from": "node3-node4-node5-node6", "to": "node7"},
      { "from": "node3-node4-node5-node6", "to": "node8"}
    ])
  })

  it("should handle group ending with split", () => {
    const process = createProcess([["node1", "node2", "node3"]])
    expect(NodeUtils.edgesFromProcess(process)).toEqual([
      { "from": "node1-node2-node3", "to": "node4"},
      { "from": "node1-node2-node3", "to": "node5"},
      { "from": "node4", "to": "node6"},
      { "from": "node5", "to": "node7"},
      { "from": "node6", "to": "node8"}
    ])
  })

})



const createProcess = (groups) => ({
  "properties": { additionalFields: { groups: groups || []}},
  "nodes": [
    {id: "node1"},
    {id: "node2"},
    {id: "node3"},
    {id: "node4"},
    {id: "node5"},
    {id: "node6"},
    {id: "node7"},
    {id: "node8"},

  ],
  "edges": [
    { "from": "node1", "to": "node2"},
    { "from": "node2", "to": "node3"},
    { "from": "node3", "to": "node4"},
    { "from": "node3", "to": "node5"},
    { "from": "node4", "to": "node6"},
    { "from": "node5", "to": "node7"},
    { "from": "node6", "to": "node8"}
  ]
})