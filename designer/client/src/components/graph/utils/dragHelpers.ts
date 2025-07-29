import type { dia, g } from "jointjs";

import type { NodeType } from "../../../types";
import { getNodeData } from "../Graph";
import { isModelElement } from "../GraphPartialsInTS";
import { rafThrottle } from "../rafThrottle";

export function getLinkNodes(link: dia.Link, graph = link.graph): { sourceNode: NodeType; targetNode: NodeType } {
    const source = graph.getCell(link.source()?.id);
    const target = graph.getCell(link.target()?.id);

    return {
        sourceNode: getNodeData(source),
        targetNode: getNodeData(target),
    };
}

function replaceAllowed(cell: dia.Cell, node?: NodeType) {
    if (!isModelElement(cell)) return false;
    const { id } = getNodeData(cell);
    const connectedLinks = cell.graph.getConnectedLinks(cell);
    const inputs = connectedLinks.filter((e) => e.target().id === id);
    const outputs = connectedLinks.filter((e) => e.source().id === id);

    switch (node?.type) {
        case "Join":
            return outputs.length <= 1;
        case "Split":
        case "Switch":
        case "FragmentInput":
            return inputs.length <= 1;
        case "Filter":
            return inputs.length <= 1 && outputs.length <= 2;
        default:
            return inputs.length <= 1 && outputs.length <= 1;
    }
}

function getDraggedOver(cell: dia.Cell) {
    return cell.get(`draggedOver`);
}

export function filterDragHovered(cells: dia.Cell[] = []): dia.Cell[] {
    return cells.filter((cell) => getDraggedOver(cell)).sort((a, b) => getDraggedOver(b) - getDraggedOver(a));
}

function getArea(el?: g.Rect): number {
    return !el ? 0 : Math.max(1, el.width) * Math.max(1, el.height);
}

export const setDraggedOver = rafThrottle((graph: dia.Graph, rect?: g.Rect, cell?: dia.Cell, nodeData?: NodeType): void => {
    graph
        .getCells()
        .filter((c) => c !== cell)
        .forEach((c) => {
            if (!rect) return;
            if (!c.isLink() && !replaceAllowed(c, nodeData || getNodeData(cell))) return;

            const box = c.getBBox();
            const coverRatio = getArea(box.intersect(rect)) / getArea(box);
            c.set(`draggedOver`, coverRatio);
        });
});
