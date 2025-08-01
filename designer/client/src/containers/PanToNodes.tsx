import type { dia } from "jointjs";
import { g } from "jointjs";
import { fromEvents, stream } from "kefir";
import { useEffect } from "react";

import { getNeighbors } from "../components/graph/getNeighbors";
import { useGraph } from "../components/graph/GraphContext";
import { isModelElement } from "../components/graph/GraphPartialsInTS";
import { Events } from "../components/graph/types";

const frameStream = stream<DOMHighResTimeStamp, unknown>((emitter) => {
    let frame: number;

    const tick: FrameRequestCallback = (time) => {
        emitter.emit(time);
        frame = requestAnimationFrame(tick);
    };

    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
});

function getCellsBox(paper: dia.Paper, cells: dia.Cell[]) {
    return cells.reduce((rect, cell) => rect.union(paper.findViewByModel(cell).getBBox()), new g.Rect());
}

export function PanToNodes() {
    const graphGetter = useGraph();

    useEffect(() => {
        const instance = graphGetter();
        if (!instance?.graph) return;
        const { graph, viewport, processGraphPaper: paper, fit } = instance;
        return fromEvents(graph, Events.ADD, (cell: dia.Cell) => cell)
            .filter(isModelElement)
            .bufferBy(frameStream.skip(2))
            .filter((cells) => cells.length > 0)
            .observe((cells) => {
                const viewBox = viewport.clone().inflate(viewport.width * -0.2, viewport.height * -0.2);
                const cellsBox = getCellsBox(paper, cells);
                if (!viewBox.containsRect(cellsBox)) {
                    const cellsToFit = cells.flatMap((cell) => getNeighbors(graph, cell, { depth: 2, withSelf: true }));
                    fit(cellsToFit.length > 1 ? cellsToFit : null);
                }
            }).unsubscribe;
    }, [graphGetter]);

    return null;
}
