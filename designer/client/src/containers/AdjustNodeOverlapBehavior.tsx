import type { dia } from "jointjs";
import { useEffect } from "react";

import { layoutChanged } from "../actions/nk";
import { findFreeSpaceForNode } from "../actions/nk/findFreeSpaceForNode";
import { useGraph } from "../components/graph/GraphContext";
import { isModelElement, isModelOrStickyNote } from "../components/graph/GraphPartialsInTS";
import { Events } from "../components/graph/types";
import { useAppDispatch } from "../store/storeHelpers";

export function AdjustNodeOverlapBehavior() {
    const dispatch = useAppDispatch();
    const graphGetter = useGraph();

    const paper = graphGetter()?.processGraphPaper;

    useEffect(() => {
        const context = {};
        paper?.model.on(
            Events.ADD,
            (cell: dia.Element) => {
                if (!isModelElement(cell)) return;
                // wait for cell.position update
                requestAnimationFrame(() => {
                    const point = findFreeSpaceForNode(paper, cell.position(), cell, cell.size());
                    cell.position(point.x, point.y);
                    const nodes = paper.model.getElements().filter(isModelOrStickyNote);
                    dispatch(
                        layoutChanged(
                            nodes.map((el) => ({
                                id: el.id.toString(),
                                position: el.position().toJSON(),
                            })),
                        ),
                    );
                });
            },
            context,
        );
        return () => {
            paper?.model.off(null, null, context);
        };
    }, [dispatch, paper]);

    return null;
}
