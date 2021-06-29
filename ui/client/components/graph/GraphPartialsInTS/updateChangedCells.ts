import {dia} from "jointjs"

export function updateChangedCells(graph: dia.Graph) {
  return (changedCells: dia.Cell[]) => {
    changedCells.forEach(cell => {
      const cellToRemove = graph.getCell(cell.id)
      const links = cellToRemove?.isElement() ? graph.getConnectedLinks(cellToRemove) : []
      graph.removeCells([...links, cellToRemove])
      graph.addCells([cell, ...links])
    })
  }
}
