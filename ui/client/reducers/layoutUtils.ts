import {defaultsDeep, isString} from "lodash"
import {GraphState, ProcessToDisplayState} from "./graphState"
import {Layout} from "../actions/nk"
import {NodeType} from "../types"
import {Reducer} from "../actions/reduxTypes"

function toString(layout: Layout): string {
  return layout.map(({id, position: {x, y}}) => `"${id}",${x},${y}`).join(";")
}

export function fromString(layoutStr: string): Layout {
  if (!layoutStr || !isString(layoutStr)) {
    return []
  }
  return layoutStr.split(/;/).map(l => {
    const [_, id, x, y] = l.split(/^"(.*)",([0-9\-]+),([0-9\-]+)/)
    return {id, position: {x: parseFloat(x), y: parseFloat(y)}}
  })
}

export const appendToProcess = (layout: Layout) => (state: ProcessToDisplayState): GraphState => defaultsDeep(
  {
    properties: {
      additionalFields: {
        properties: {
          layout: toString(layout),
        },
      },
    },
  },
  state,
)

export const nodes: Reducer<NodeType[]> = (nodes, action) => {
  switch (action.type) {
    case "LAYOUT_CHANGED":
      return nodes.map(node => {
        const layoutData = action.layout.find(({id}) => id === node.id)?.position || null
        return defaultsDeep({additionalFields: {layoutData}}, node)
      })
    default:
      return nodes
  }
}
