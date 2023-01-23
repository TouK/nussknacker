import {defaultsDeep} from "lodash"
import {Layout} from "../actions/nk"
import {NodeType, Process} from "../types"
import {Reducer} from "../actions/reduxTypes"

export function fromMeta(process: Process): Layout {
  return process.nodes
    .filter(({additionalFields}) => additionalFields?.layoutData)
    .map(({id, additionalFields}) => ({id, position: additionalFields.layoutData}))
}

export const nodes: Reducer<NodeType[]> = (nodes, action) => {
  switch (action.type) {
    case "LAYOUT_CHANGED":
      return nodes?.map(node => {
        const layoutData = action.layout.find(({id}) => id === node.id)?.position || null
        return defaultsDeep({additionalFields: {layoutData}}, node)
      })
    default:
      return nodes
  }
}
