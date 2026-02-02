import type { PopoverProps } from "@mui/material";
import { Grow, Paper, Popper } from "@mui/material";
import type { dia } from "jointjs";
import type { MutableRefObject } from "react";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { getScenarioGraph } from "../../reducers/selectors/graph";
import { useAppSelector } from "../../store/storeHelpers";
import type { Graph } from "./Graph";
import { getNodeData } from "./Graph";
import { isStickyNoteElement } from "./GraphPartialsInTS/cellUtils";
import { MarkdownStyled } from "./node-modal/MarkdownStyled";
import { Events } from "./types";
import { watchForCover } from "./watchForCover";

const useTimeout = <A extends Array<unknown>>(
    callback: (...args: A) => void,
    time: number,
): {
    start: (...args: A) => () => void;
    stop: () => void;
} => {
    const timer = useRef<NodeJS.Timeout>(null);

    const stop = useCallback(() => {
        if (timer.current) {
            clearTimeout(timer.current);
            timer.current = null;
        }
    }, []);

    const start = useCallback(
        (...args: A) => {
            stop();
            timer.current = setTimeout(() => {
                callback(...args);
            }, time);
            return stop;
        },
        [callback, stop, time],
    );

    return { start, stop };
};

const useEnterLeaveEvents = (
    graphRef: React.MutableRefObject<Graph | undefined>,
    {
        onEnter,
        onLeave,
        innerSelector,
        outerSelector,
    }: {
        onEnter: (view: dia.CellView, el?: Element) => void;
        onLeave: (view: dia.CellView, el?: Element) => void;
        innerSelector: string | string[];
        outerSelector: string;
    },
) => {
    const lastView = useRef<dia.CellView>(null);
    const lastEl = useRef<Element | null>(null);

    useEffect(() => {
        const graph = graphRef.current;

        const withView =
            <E extends JQuery.TriggeredEvent<HTMLElement>>(callback: (view: dia.CellView, event: E) => void) =>
            (event: E) => {
                const view = graph?.processGraphPaper?.findView(event.target);
                callback(view, event);
            };

        const withEl =
            <T extends Element, E extends JQuery.TriggeredEvent<T>>(callback: (view: dia.CellView, el?: T) => void, inner?: boolean) =>
            (view: dia.CellView, event: E) => {
                callback(view, inner ? event.target : undefined);
            };

        const getEvents = (inner?: boolean): JQuery.TypeEventHandlers<HTMLElement, undefined, any, any> => {
            return {
                mouseover: withView(
                    withEl((view: dia.CellView, el?: Element) => {
                        if (lastView.current === view || lastEl.current === el) return;

                        onEnter(view, el);
                        lastEl.current = el;
                        lastView.current = view;
                    }, inner),
                ),
                mouseout: withView(
                    withEl((view: dia.CellView, el?: Element) => {
                        if (lastView.current !== view && lastEl.current !== el) return;

                        onLeave(view, el);
                        lastEl.current = null;
                        lastView.current = null;
                    }, inner),
                ),
            };
        };

        const eventsInner = getEvents(true);
        const eventsOuter = getEvents();
        const innerSel = Array.isArray(innerSelector) ? innerSelector.map((s) => s.trim()).join(",") : innerSelector;
        graph?.processGraphPaper?.$el?.on(eventsInner, innerSel);
        graph?.processGraphPaper?.$el?.on(eventsOuter, outerSelector);
        return () => {
            graph?.processGraphPaper?.$el?.off(eventsInner, innerSel);
            graph?.processGraphPaper?.$el?.off(eventsOuter, outerSelector);
        };
    }, [onEnter, graphRef, innerSelector, outerSelector, onLeave]);
};

type NodeDescriptionPopoverProps = {
    graphRef: MutableRefObject<Graph>;
    leaveTimeout?: number;
    enterTimeout?: number;
};

function withFallback(anchorEl: PopoverProps["anchorEl"]) {
    const el = typeof anchorEl === "function" ? anchorEl() : anchorEl;
    if (!el) return null;
    if ("isConnected" in el && !el.isConnected) return document.body;
    return el;
}

// TODO: show rendered description somehow on touch screens
export function NodeDescriptionPopover(props: NodeDescriptionPopoverProps) {
    const { t } = useTranslation();
    const { graphRef, enterTimeout = 800, leaveTimeout = 200 } = props;
    const scenarioGraph = useAppSelector(getScenarioGraph);
    const [open, setOpen] = useState(false);
    const [[description, anchorEl], setData] = useState<[string, PopoverProps["anchorEl"]]>([null, null]);
    const lastTarget = useRef<Element | null>(null);

    const enterTimer = useTimeout((view: dia.CellView, el: Element) => {
        if (isStickyNoteElement(view.model)) return; //Dont use it for stickyNotes

        const hasAssertionResultHovered = el?.attributes["joint-selector"]?.value === "testAssertionResult";
        setData(
            hasAssertionResultHovered
                ? [t("graph.node.assertionResult.title", "Passed assertions out of total assertions for this node"), el]
                : el
                ? [t("graph.node.counts.title", "number of messages that passed downstream"), el]
                : [getNodeData(view.model, scenarioGraph)?.additionalFields?.description, view.el],
        );
        setOpen(true);
        lastTarget.current = el || view.el;
    }, enterTimeout);
    const leaveTimer = useTimeout(() => {
        setOpen(false);
        enterTimer.stop();
        lastTarget.current = null;
    }, leaveTimeout);

    useEnterLeaveEvents(graphRef, {
        onEnter: (view, el) => {
            if (lastTarget.current !== (el || view.el)) {
                setOpen(false);
            }
            leaveTimer.stop();
            enterTimer.start(view, el);
        },
        onLeave: () => {
            leaveTimer.start();
        },
        innerSelector: ["[joint-selector=testResultsGroup]", "[joint-selector=testAssertionResultGroup]"],
        outerSelector: "[model-id]",
    });

    useEffect(() => {
        if (!open) return;
        return watchForCover(
            () => lastTarget.current,
            () => leaveTimer.start(),
        );
    }, [leaveTimer, open]);

    useEffect(() => {
        const graph = graphRef.current;
        const callback = () => setOpen(false);
        graph?.processGraphPaper?.on(Events.CELL_POINTERDOWN, callback);
        return () => {
            graph?.processGraphPaper?.off(Events.CELL_POINTERDOWN, callback);
        };
    }, [graphRef]);

    return (
        <Popper
            open={open}
            anchorEl={withFallback(anchorEl)}
            sx={{
                zIndex: (t) => t.zIndex.tooltip,
            }}
            transition
        >
            {({ TransitionProps }) => (
                <Grow {...TransitionProps} in={TransitionProps.in && !!description}>
                    <Paper elevation={5} onMouseLeave={() => leaveTimer.start()} onMouseEnter={() => leaveTimer.stop()}>
                        <MarkdownStyled
                            sx={{
                                maxWidth: (t) => t.breakpoints.values.sm,
                                mt: 0,
                                px: 2,
                                py: 0,
                                overflow: "auto",
                                zoom: 0.9,
                            }}
                        >
                            {description}
                        </MarkdownStyled>
                    </Paper>
                </Grow>
            )}
        </Popper>
    );
}
