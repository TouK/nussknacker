import * as dagre from "dagre";
import { dia, layout, util } from "jointjs";
import { isCellSelected, isModelElement } from "./cellUtils";

export function getCellsToLayout(graph: dia.Graph, selectedItems: string[]): dia.Cell[] {
    const cells = graph.getCells();
    const modelsAndLinks = cells.filter((cell) => cell.isLink() || isModelElement(cell));
    const selectedCells = modelsAndLinks.filter(isCellSelected(selectedItems));
    return selectedCells.length > 1 ? selectedCells : modelsAndLinks;
}

export function calcLayout(graph: dia.Graph, cellsToLayout: dia.Cell[] = []): void {
    if (cellsToLayout.length) {
        const graphPoint = graph.getBBox().topLeft();
        const alignPoint = graph.getCellsBBox(cellsToLayout).topLeft();
        layout.DirectedGraph.layout(cellsToLayout, {
            graphlib: dagre.graphlib,
            dagre: dagre,
            nodeSep: 60,
            edgeSep: 0,
            rankSep: 102,
            rankDir: "TB",
        });
        if (!util.isEqual(graphPoint, alignPoint)) {
            const centerAfter = graph.getCellsBBox(cellsToLayout).topLeft();
            cellsToLayout
                .filter((c) => c.isElement())
                .forEach((c: dia.Element) => c.translate(alignPoint.x - centerAfter.x, alignPoint.y - centerAfter.y));
        }
    }
}
