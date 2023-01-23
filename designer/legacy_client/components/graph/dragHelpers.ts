import {dia, g} from "jointjs"
import {NodeType} from "../../types"
import {rafThrottle} from "./rafThrottle"

export function getLinkNodes(link: dia.Link): { sourceNode: NodeType, targetNode: NodeType } {
  const {graph} = link
  const source = graph.getCell(link.getSourceElement()?.id)
  const target = graph.getCell(link.getTargetElement()?.id)

  return {
    sourceNode: source?.get(`nodeData`),
    targetNode: target?.get(`nodeData`),
  }
}

export function filterDragHovered(links: dia.Link[] = []): dia.Link[] {
  return links
    .filter(l => l.get(`draggedOver`))
    .sort((a, b) => b.get(`draggedOver`) - a.get(`draggedOver`))
}

function getArea(el: g.Rect): number {
  return !el ? 0 : Math.max(1, el.width) * Math.max(1, el.height)
}

export const setLinksHovered = rafThrottle((graph: dia.Graph, rect?: g.Rect): void => {
  graph.getLinks().forEach(l => {
    let coverRatio = 0
    if (rect) {
      const box = l.getBBox()
      coverRatio = getArea(box.intersect(rect)) / getArea(box)
    }
    l.set(`draggedOver`, coverRatio)
  })
})
