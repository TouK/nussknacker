import {Layout} from "../../../actions/nk"
import {isEmpty} from "lodash"
import {Graph} from "../Graph"
import {dia} from "jointjs"

export function updateLayout(graph: dia.Graph, directedLayout: typeof Graph.prototype.directedLayout) {
  return (layout: Layout) => {
    if (isEmpty(layout)) {
      directedLayout()
    } else {
      layout.forEach(node => {
        const cell = graph.getCell(node.id)
        if (cell && JSON.stringify(cell.get("position")) !== JSON.stringify(node.position)) cell.set("position", node.position)
      })
    }
  }
}
