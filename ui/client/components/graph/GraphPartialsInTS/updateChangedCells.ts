import {dia} from "jointjs"

export function updateChangedCells(graph: dia.Graph) {
  return (changedCells: dia.Cell[]) => {
    changedCells.forEach(cell => {
      const cellToRemove = graph.getCell(cell.id)
      const links = cellToRemove.isElement ? graph.getConnectedLinks(cellToRemove) : []
      cellToRemove.remove()
      graph.addCell(cell)
      links.forEach(link => {
        link.remove()
        graph.addCell(link)
      })
    })
  }
}
