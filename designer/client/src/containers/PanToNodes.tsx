import type { dia } from "jointjs";
import { g } from "jointjs";
import { fromEvents } from "kefir";
import { useEffect } from "react";

import { getNeighbors } from "../components/graph/getNeighbors";
import { useGraph } from "../components/graph/GraphContext";
import { isModelElement } from "../components/graph/GraphPartialsInTS";
import { Events } from "../components/graph/types";

export function PanToNodes() {
    const graphGetter = useGraph();

    useEffect(() => {
        const instance = graphGetter();
        if (!instance?.graph) return;
        const { graph, viewport, processGraphPaper: paper, fit } = instance;
        return fromEvents(graph, Events.ADD, (cell: dia.Cell) => cell)
            .filter(isModelElement)
            .bufferWithTimeOrCount(250, Infinity)
            .filter((cells) => cells.length > 0)
            .observe((cells) => {
                const viewBox = viewport.clone().inflate(viewport.width * -0.2, viewport.height * -0.2);
                const cellsBox = cells.reduce((rect, cell) => rect.union(paper.findViewByModel(cell).getBBox()), new g.Rect());
                if (!viewBox.containsRect(cellsBox)) {
                    const cellsToFit = cells.flatMap((cell) => getNeighbors(graph, cell, { depth: 2, withSelf: true }));
                    fit(cellsToFit.length > 1 ? cellsToFit : null);
                }
            }).unsubscribe;
    }, [graphGetter]);

    return null;
}
