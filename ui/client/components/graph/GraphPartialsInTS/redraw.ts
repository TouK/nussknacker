import {updateChangedCells} from "./updateChangedCells"
import {dia} from "jointjs"

export function redraw(graph: dia.Graph) {
  const updateCells = updateChangedCells(graph)
  return (newCells: dia.Cell[], deletedCells: dia.Cell[], changedCells: dia.Cell[]) => {
    graph.removeCells(deletedCells)
    updateCells(changedCells)
    graph.addCells(newCells)
  }
}
