import {dia} from "jointjs"
import {isEmpty} from "lodash"
import {Layout} from "../../../actions/nk"
import {Graph} from "../Graph"
import {isElement} from "./cellUtils"

export function updateLayout(graph: dia.Graph, directedLayout: typeof Graph.prototype.directedLayout) {
  return (layout: Layout): void => {
    if (isEmpty(layout)) {
      directedLayout()
    } else {
      layout.forEach(({position, id}) => {
        const cell = graph.getCell(id)
        if (isElement(cell) && JSON.stringify(cell.position()) !== JSON.stringify(position)) {
          cell.position(position.x, position.y)
        }
      })
    }
  }
}
