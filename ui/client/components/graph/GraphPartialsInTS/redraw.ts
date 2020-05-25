import {updateChangedCells} from "./updateChangedCells"
import {dia} from "jointjs"

export function redraw(graph: dia.Graph) {
  const updateCells = updateChangedCells(graph)
  return (newCells: dia.Cell[], deletedCells: dia.Cell[], changedCells: dia.Cell[], cells: dia.Cell[]) => {
    if (newCells.length + deletedCells.length + changedCells.length > 3) {
      graph.resetCells(cells)
    } else {
      graph.removeCells(deletedCells)
      updateCells(changedCells)
      graph.addCells(newCells)
    }
  }
}
