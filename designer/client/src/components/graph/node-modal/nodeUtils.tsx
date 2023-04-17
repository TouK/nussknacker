import {NodeType, Process} from "../../../types"
import {cloneDeep, get, has} from "lodash"
import {v4 as uuid4} from "uuid"

export function generateUUIDs(editedNode: NodeType, properties: string[]): NodeType {
  const node = cloneDeep(editedNode)
  properties.forEach((property) => {
    if (has(node, property)) {
      get(node, property, []).forEach((el) => el.uuid = el.uuid || uuid4())
    }
  })
  return node
}

export function getNodeId(processToDisplay: Process, node: NodeType): string {
  return processToDisplay.properties.isSubprocess ? node.id.replace(`${processToDisplay.id}-`, "") : node.id
}
