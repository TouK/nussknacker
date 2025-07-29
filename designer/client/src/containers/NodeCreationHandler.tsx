import type { dia } from "jointjs";
import { g } from "jointjs";
import { useEffect } from "react";
import { useDispatch } from "react-redux";
import { useKey } from "rooks";

import type { PanelSide } from "../actions/nk";
import { nodesWithEdgesAdded } from "../actions/nk";
import { portSize, RECT_HEIGHT, RECT_WIDTH } from "../components/graph/EspNode/esp";
import { useGraph } from "../components/graph/GraphContext";
import { useSidePanel } from "../components/sidePanels/SidePanelsContext";
import { globalEventBus } from "../components/toolbars/creator/globalEventBus";
import { ComponentFilter } from "../components/toolbars/creator/ToolBox";
import { useOutsideInteraction } from "../components/toolbars/creator/useOutsideInteraction";

const adjustPoint = (paper: dia.Paper, plainPoint: g.PlainPoint): g.Point => {
    const rect = new g.Rect(plainPoint.x, plainPoint.y, RECT_WIDTH, RECT_HEIGHT);
    if (paper.findViewsInArea(rect.clone().inflate(10)).length > 0) {
        return adjustPoint(paper, rect.offset(RECT_HEIGHT).topLeft());
    }
    return rect.topLeft().snapToGrid(1, 1);
};

export function NodeCreationHandler({ panelSide }: { panelSide: PanelSide }) {
    const dispatch = useDispatch();
    const graphGetter = useGraph();

    useEffect(() => {
        const paper = graphGetter()?.processGraphPaper;
        if (!paper) return;

        paper.options.linkPinning = true;
        const context = {};

        paper.on(
            "blank:contextmenu",
            (event, x, y) => {
                globalEventBus.emit("openNodeSelector", {
                    side: panelSide,
                    fromPoint: new g.Point(x, y).offset(RECT_WIDTH * -0.5),
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
                globalEventBus.emit("openNodeSelector", {
                    side: panelSide,
                    fromPoint: position,
                    filters: [isLinkReversed ? ComponentFilter.removeNoOutputs : ComponentFilter.removeNoInputs],
                    withEdge: { ...edgeData, from, to },
                });

                globalEventBus.once("closeNodeSelector", () => {
                    link.remove();
                });
            },
            context,
        );
        return () => {
            paper.off(null, null, context);
            paper.options.linkPinning = false;
        };
    }, [graphGetter, panelSide]);

    const { isOpened, toggleCollapse, ref } = useSidePanel(panelSide);

    useEffect(() => {
        return globalEventBus.on("closeNodeSelector", ({ node, onPoint, side, edge }) => {
            if (side !== panelSide) return;
            toggleCollapse();

            if (!node) return;

            const graph = graphGetter();
            const paper = graph.processGraphPaper;

            const position: g.Point = adjustPoint(paper, onPoint);

            if (graph.isFragmentCreator(node)) {
                return graph.createFragment(position, edge);
            }

            dispatch(nodesWithEdgesAdded([{ node, position }], [edge].filter(Boolean), false));
        });
    }, [dispatch, graphGetter, panelSide, toggleCollapse]);

    const justClose = () => globalEventBus.emit("closeNodeSelector", { side: panelSide });

    useOutsideInteraction(ref, justClose, isOpened);
    useKey("Escape", justClose, { when: isOpened });

    return null;
}
