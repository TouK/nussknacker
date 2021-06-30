import {dia, g} from "jointjs"

export function filterDragHovered(links: dia.Link[] = []): dia.Link[] {
  return links
    .filter(l => l.get("draggedOver"))
    .sort((a, b) => b.get("draggedOver") - a.get("draggedOver"))
}

function getArea(el: g.Rect): number {
  return !el ? 0 : Math.max(1, el.width) * Math.max(1, el.height)
}

export function setLinksHovered(graph: dia.Graph, rect?: g.Rect): void {
  graph.getLinks().forEach(l => {
    const box = l.getBBox()
    l.set({
      draggedOver: rect ? getArea(box.intersect(rect)) / getArea(box) : 0,
    })
  })
}
