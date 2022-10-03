import {dia} from "jointjs"
import {Layout} from "../../../actions/nk"
import {isElement} from "./cellUtils"

export const updateLayout = (graph: dia.Graph, layout: Layout): void => {
  layout.forEach(({position, id}) => {
    const cell = graph.getCell(id)
    if (isElement(cell) && JSON.stringify(cell.position()) !== JSON.stringify(position)) {
      cell.position(position.x, position.y)
    }
  })
}
