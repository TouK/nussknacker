import { g } from "jointjs";
import { useEffect } from "react";
import { useKey } from "rooks";

import type { PanelSide } from "../actions/nk/ui/panelSide";
import { portSize, RECT_HEIGHT, RECT_WIDTH } from "../components/graph/EspNode/esp";
import { useGraph } from "../components/graph/GraphContext";
import { useSidePanel } from "../components/sidePanels/SidePanelsContext";
import { closeNodeSelector, openNodeSelector } from "../components/toolbars/creator/nodeSelectorActions";
import { ComponentFilter } from "../components/toolbars/creator/ToolBox";
import { useOutsideInteraction } from "../components/toolbars/creator/useOutsideInteraction";
import { addListenerTyped, addOnceListenerTyped, useAppDispatch } from "../store/storeHelpers";

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
                dispatch(
                    openNodeSelector({
                        side: panelSide,
                        fromPoint: new g.Point(x, y).offset(RECT_WIDTH * -0.5),
                    }),
                );
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
                if (!graph) return;

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

                dispatch(
                    openNodeSelector({
                        side: panelSide,
                        fromPoint: position,
                        filters: [isLinkReversed ? ComponentFilter.removeNoOutputs : ComponentFilter.removeNoInputs],
                        withEdge: {
                            ...edgeData,
                            from,
                            to,
                        },
                    }),
                );
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
            addListenerTyped("CLOSE_NODE_SELECTOR", ({ data: { node, side } }, api) => {
                if (side === panelSide) toggleCollapse();
                if (!node) return;
            }),
        );
    }, [dispatch, graphGetter, panelSide, toggleCollapse, when]);

    useOutsideInteraction(ref, () => dispatch(closeNodeSelector(panelSide)), isOpened && when);
    useKey("Escape", () => dispatch(closeNodeSelector(panelSide)), { when: isOpened && when });
}
