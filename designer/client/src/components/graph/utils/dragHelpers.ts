import { dia, g } from "jointjs";
import { NodeType } from "../../../types";
import { getNodeData } from "../Graph";
import { isModelElement } from "../GraphPartialsInTS";
import { rafThrottle } from "../rafThrottle";

export function getLinkNodes(link: dia.Link): { sourceNode: NodeType; targetNode: NodeType } {
    const { graph } = link;
    const source = graph.getCell(link.getSourceElement()?.id);
    const target = graph.getCell(link.getTargetElement()?.id);

    return {
        sourceNode: getNodeData(source),
        targetNode: getNodeData(target),
    };
}

function replaceAllowed(cell: dia.Cell) {
    if (!isModelElement(cell)) return false;
    const { type } = getNodeData(cell);
    return !["Split", "Join", "Filter", "Switch"].includes(type);
}

function getDraggedOver(cell: dia.Cell) {
    return cell.get(`draggedOver`);
}

export function filterDragHovered(links: dia.Cell[] = []): dia.Cell[] {
    return links
        .filter((cell) => {
            if (!getDraggedOver(cell)) return false;
            if (cell.isLink()) return true;
            if (replaceAllowed(cell)) return true;
            return false;
        })
        .sort((a, b) => getDraggedOver(b) - getDraggedOver(a));
}

function getArea(el: g.Rect): number {
    return !el ? 0 : Math.max(1, el.width) * Math.max(1, el.height);
}

export const setLinksHovered = rafThrottle((graph: dia.Graph, rect?: g.Rect, cell?: dia.Cell): void => {
    graph
        .getCells()
        .filter((c) => c !== cell)
        .forEach((c) => {
            let coverRatio = 0;
            if (rect) {
                const box = c.getBBox();
                coverRatio = getArea(box.intersect(rect)) / getArea(box);
            }
            c.set(`draggedOver`, coverRatio);
        });
});
