import * as dagre from "dagre"
import {dia, layout, util} from "jointjs"
import {isCellSelected, isElement, isLink, isModelElement} from "./cellUtils"

export function directedLayout(selectedItems: string[] = []): void {
  this.redrawing = true
  const graph = this.graph
  //TODO `layout` method can take graph or cells
  //when joint.layout.DirectedGraph.layout(this.graph) is used here
  //  then `toFront()` method works as expected but there are issues with group fold/unfold
  //when joint.layout.DirectedGraph.layout(this.graph.getCells().filter(cell => !cell.get('backgroundObject')) is used here
  // then `toFront()` method does not work at all, but group fold/unfold works just fine
  const cells = graph.getCells()
  const modelsAndLinks = cells.filter(cell => cell.isLink() || isModelElement(cell))
  const selectedCells = modelsAndLinks.filter(isCellSelected(selectedItems))

  calcLayout(graph, selectedCells.length > 1 ? selectedCells : modelsAndLinks)

  this.redrawing = false
  this.changeLayoutIfNeeded()
}

const calcLayout = (graph: dia.Graph, cellsToLayout: Array<dia.Element | dia.Link>) => {
  if (!cellsToLayout.length) {
    return
  }

  const graphPoint = graph.getBBox().topLeft()
  const alignPoint = graph.getCellsBBox(cellsToLayout).topLeft()

  layout.DirectedGraph.layout(cellsToLayout, {
    graphlib: dagre.graphlib,
    dagre: dagre,
    nodeSep: 60,
    edgeSep: 0,
    rankSep: 120,
    rankDir: "TB",
  })

  if (!util.isEqual(graphPoint, alignPoint)) {
    const centerAfter = graph.getCellsBBox(cellsToLayout).topLeft()
    cellsToLayout.forEach(c => c.translate(alignPoint.x - centerAfter.x, alignPoint.y - centerAfter.y))
  }
}
