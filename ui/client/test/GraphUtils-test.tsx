import {GraphState} from "../reducers/graph"
import {canGroupSelection} from "../reducers/graph/utils"

const state = {
  processToDisplay: {
    properties: {
      additionalFields: {
        groups: [
          {
            id: "D-E",
            nodes: ["D", "E"],
          },
        ],
      },
    },
    nodes: [
      {id: "A"},
      {id: "B"},
      {id: "C"},
      {id: "D"},
      {id: "E"},
      {id: "F"},
      {id: "switch"},
      {id: "1A"},
      {id: "1B"},
      {id: "1C"},
      {id: "2A"},
      {id: "2B"},
      {id: "2C"},
    ],
    edges: [
      {from: "A", to: "B"},
      {from: "B", to: "C"},
      {from: "C", to: "D"},
      {from: "D", to: "E"},
      {from: "E", to: "F"},
      {from: "F", to: "switch"},
      {from: "switch", to: "1A"},
      {from: "1A", to: "1B"},
      {from: "1B", to: "1C"},
      {from: "switch", to: "2A"},
      {from: "2A", to: "2B"},
      {from: "2B", to: "2C"},
    ],
  },
  selectionState: [],
} as GraphState

const prepareState = (selectionState: string[]): GraphState => ({...state, selectionState})

describe("Graph utils", () => {
  describe("canGroupSelection", () => {
    it("should drop empty", () => {
      expect(canGroupSelection(prepareState([]))).toBeFalsy()
    })

    it("should drop single", () => {
      expect(canGroupSelection(prepareState(["B"]))).toBeFalsy()
    })

    it("should drop group", () => {
      expect(canGroupSelection(prepareState(["B", "C", "D-E", "F"]))).toBeFalsy()
    })

    it("should accept connected", () => {
      expect(canGroupSelection(prepareState(["B", "C", "D", "E", "F"]))).toBeTruthy()
    })

    it("should accept forks", () => {
      expect(canGroupSelection(prepareState(["F", "switch", "1A", "1B"]))).toBeTruthy()
      expect(canGroupSelection(prepareState(["F", "switch", "2A", "2B"]))).toBeTruthy()
      expect(canGroupSelection(prepareState(["F", "switch", "1A", "1B", "2A", "2B"]))).toBeTruthy()
    })

    it("should drop not connected", () => {
      expect(canGroupSelection(prepareState(["A", "C", "D", "E"]))).toBeFalsy()
      expect(canGroupSelection(prepareState(["B", "C", "E", "F"]))).toBeFalsy()
      expect(canGroupSelection(prepareState(["E", "F", "1A", "1B", "2A", "2B"]))).toBeFalsy()
    })
  })
})
