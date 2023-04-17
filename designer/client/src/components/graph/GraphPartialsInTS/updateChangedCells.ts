import {dia} from "jointjs"

export const updateChangedCells = (graph: dia.Graph, changedCells: dia.Cell[]): void => {
  changedCells.forEach(cell => {
    const cellToRemove = graph.getCell(cell.id)
    const links = cellToRemove?.isElement() ? graph.getConnectedLinks(cellToRemove) : []
    graph.removeCells([...links, cellToRemove])
    graph.addCells([cell, ...links])
  })
}
