import { g } from "jointjs";
import { useEffect } from "react";
import { useDispatch } from "react-redux";
import { useKey } from "rooks";

import { nodesWithEdgesAdded, PanelSide } from "../actions/nk";
import { portSize, RECT_HEIGHT, RECT_WIDTH } from "../components/graph/EspNode/esp";
import { useGraph } from "../components/graph/GraphContext";
import { useSidePanel } from "../components/sidePanels/SidePanelsContext";
import { globalEventBus } from "../components/toolbars/creator/globalEventBus";
import { useOutsideInteraction } from "../components/toolbars/creator/useOutsideInteraction";

export function NodeCreationHandler() {
    const dispatch = useDispatch();
    const graphGetter = useGraph();
    const panelSide = PanelSide.RightDynamic;

    useEffect(() => {
        const paper = graphGetter()?.processGraphPaper;
        if (!paper) return;

        const context = {};

        paper.on(
            "blank:contextmenu",
            (event, x, y) => {
                globalEventBus.emit("openNodeSelector", {
                    side: panelSide,
                    fromPoint: new g.Point(x, y).offset(RECT_WIDTH * -0.5, RECT_HEIGHT * -0.5),
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
                    while (paper.findViewsFromPoint(position).length > 0) {
                        position = position.offset(RECT_HEIGHT);
                    }
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
                    filters: [isLinkReversed ? "removeNoOutputs" : "removeNoInputs"],
                    withEdge: { ...edgeData, from, to },
                });

                // globalEventBus.once("closeNodeSelector", () => {
                //     link.remove();
                // });
            },
            context,
        );
        return () => {
            paper.off(null, null, context);
        };
    }, [graphGetter, panelSide]);

    useEffect(
        () =>
            globalEventBus.on("closeNodeSelector", ({ node, onPoint, side, edge }) => {
                if (!node) return;
                if (side !== PanelSide.RightDynamic) return;
                dispatch(
                    nodesWithEdgesAdded(
                        [
                            {
                                node,
                                position: new g.Point(onPoint).snapToGrid(1, 1),
                            },
                        ],
                        [edge].filter(Boolean),
                        false,
                    ),
                );
            }),
        [dispatch],
    );

    const justClose = () => globalEventBus.emit("closeNodeSelector", { side: panelSide });

    const { isOpened, ref } = useSidePanel(panelSide);
    useOutsideInteraction(ref, justClose, isOpened);
    useKey("Escape", justClose, { when: isOpened });

    return null;
}
