import * as joint from "jointjs"
import * as dagre from "dagre"
import {isBackgroundObject} from "./isBackgroundObject"

export function directedLayout() {
  const graph = this.graph
  //TODO `layout` method can take graph or cells
  //when joint.layout.DirectedGraph.layout(this.graph) is used here
  //  then `toFront()` method works as expected but there are issues with group fold/unfold
  //when joint.layout.DirectedGraph.layout(this.graph.getCells().filter(cell => !cell.get('backgroundObject')) is used here
  // then `toFront()` method does not work at all, but group fold/unfold works just fine
  const cells = graph.getCells().filter(cell => !isBackgroundObject(cell))
  joint.layout.DirectedGraph.layout(cells, {
    graphlib: dagre.graphlib,
    dagre: dagre,
    nodeSep: 60,
    edgeSep: 0,
    rankSep: 120,
    rankDir: "TB",
  })
  this.changeLayoutIfNeeded()
}
