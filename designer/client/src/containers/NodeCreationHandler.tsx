import type { dia } from "jointjs";
import { g } from "jointjs";
import { useEffect } from "react";
import { useKey } from "rooks";

import { nodesWithEdgesAdded } from "../actions/nk";
import type { PanelSide } from "../actions/nk/ui/panelSide";
import { portSize, RECT_HEIGHT, RECT_WIDTH } from "../components/graph/EspNode/esp";
import { useGraph } from "../components/graph/GraphContext";
import { useSidePanel } from "../components/sidePanels/SidePanelsContext";
import { closeNodeSelector, openNodeSelector } from "../components/toolbars/creator/nodeSelectorActions";
import { useOutsideInteraction } from "../components/toolbars/creator/useOutsideInteraction";
import { addListenerTyped, addOnceListenerTyped, useAppDispatch } from "../store/storeHelpers";

export function findCellsInArea(paper: dia.Paper, area: g.Rect): dia.Cell[] {
    const model = paper.model;
    const links = model.getLinks().filter((link) => {
        const view = paper.findViewByModel(link);
        if (!view) return false;
        const pathBBox = view.getBBox();
        return paper.clientToLocalRect(pathBBox).intersect(area);
    });
    const elements = model.findModelsInArea(area);
    return [...elements, ...links];
}

export const findFreeSpaceForNode = (paper: dia.Paper, plainPoint: g.PlainPoint): g.Point => {
    const rect = new g.Rect(plainPoint.x, plainPoint.y, RECT_WIDTH, RECT_HEIGHT);
    if (findCellsInArea(paper, rect.clone().inflate(10)).length > 0) {
        return findFreeSpaceForNode(paper, rect.offset(RECT_HEIGHT).topLeft());
    }
    return rect.topLeft().snapToGrid(1, 1);
};

export function useNodeCreationHandler({ panelSide, when = true }: { panelSide: PanelSide; when?: boolean }) {
    const dispatch = useAppDispatch();
    const graphGetter = useGraph();

    useEffect(() => {
        if (!when) return;

        const paper = graphGetter()?.processGraphPaper;
        if (!paper) return;

        paper.options.linkPinning = true;
        const context = {};

        paper.on(
            "blank:contextmenu",
            (event, x, y) => {
                dispatch({
                    type: "OPEN_NODE_SELECTOR",
                    data: {
                        side: panelSide,
                        fromPoint: new g.Point(x, y).offset(RECT_WIDTH * -0.5),
                    },
                });
            },
            context,
        );

        paper.on(
            "cell:pointerup",
            (cellView, event, x, y) => {
                const link = cellView.model;
                if (!link.isLink()) return;

                const target = link.target();
                if (target.id) return;

                const graph = link.graph;
                const paper = cellView.paper;
                const source = link.source();
                const cell = graph.getCell(source.id);
                const isLinkReversed = source.port === "In";
                const [from, to] = isLinkReversed ? [undefined, source.id.toString()] : [source.id.toString(), undefined];

                const { end, start } = link.getPolyline();
                const isTooShortToDisplay = start.distance(end) < RECT_HEIGHT;

                let position: g.Point;
                if (isTooShortToDisplay) {
                    link.remove();
                    position = cell.position().offset(0, (isLinkReversed ? -3 : 3) * RECT_HEIGHT);
                } else {
                    position = new g.Point(x, y).offset(
                        RECT_WIDTH * -0.8,
                        isLinkReversed ? portSize * -0.75 - RECT_HEIGHT : portSize * 0.75,
                    );
                }

                const edgeData = link.prop("edgeData");

                dispatch(openNodeSelector(panelSide, position, isLinkReversed, edgeData, from, to));
                dispatch(
                    addOnceListenerTyped("CLOSE_NODE_SELECTOR", () => {
                        link.remove();
                    }),
                );
            },
            context,
        );
        return () => {
            paper.off(null, null, context);
            paper.options.linkPinning = false;
        };
    }, [when, dispatch, graphGetter, panelSide]);

    const { isOpened, toggleCollapse, ref } = useSidePanel(panelSide);

    useEffect(() => {
        if (!when) return;
        return dispatch(
            addListenerTyped("CLOSE_NODE_SELECTOR", ({ data: { node, onPoint, side, edge } }, api) => {
                if (side !== panelSide) return;
                toggleCollapse();

                if (!node) return;

                const graph = graphGetter();
                const paper = graph.processGraphPaper;

                const position: g.Point = findFreeSpaceForNode(paper, onPoint);

                if (graph.isFragmentCreator(node)) {
                    return graph.createFragment(position, edge);
                }

                api.dispatch(
                    nodesWithEdgesAdded(
                        [
                            {
                                node,
                                position,
                            },
                        ],
                        [edge].filter(Boolean),
                        false,
                    ),
                );
            }),
        );
    }, [dispatch, graphGetter, panelSide, toggleCollapse, when]);

    useOutsideInteraction(ref, () => dispatch(closeNodeSelector(panelSide)), isOpened && when);
    useKey("Escape", () => dispatch(closeNodeSelector(panelSide)), { when: isOpened && when });
}
