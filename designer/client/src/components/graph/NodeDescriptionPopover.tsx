import React, { MutableRefObject, useCallback, useEffect, useRef, useState } from "react";
import { Graph } from "./Graph";
import { Grow, Paper, PopoverProps, Popper } from "@mui/material";
import { dia, g } from "jointjs";
import { NodeType } from "../../types";
import { Events } from "./types";
import { MarkdownStyled } from "./node-modal/MarkdownStyled";

function getDomRectFromBBox(paper: dia.Paper, bBox: g.Rect) {
    return DOMRect.fromRect(paper.localToPageRect(paper.paperToLocalRect(bBox)));
}

function getBoundingClientRectFromView(view: dia.CellView) {
    const bBox = view.getBBox({ useModelGeometry: true });
    const paper = view.paper;
    return getDomRectFromBBox(paper, bBox);
}

// TODO: show rendered description somehow on touch screens
export function NodeDescriptionPopover(props: { graphRef: MutableRefObject<Graph>; leaveTimeout?: number; enterTimeout?: number }) {
    const { graphRef, enterTimeout = 750, leaveTimeout = 250 } = props;

    const [open, setOpen] = useState(false);
    const [description, setDescription] = useState<string>();
    const [anchorEl, setAnchorEl] = useState<PopoverProps["anchorEl"]>(null);

    const hideDescription = useCallback(() => {
        setOpen(false);
    }, []);

    const showDescription = useCallback((view: dia.CellView) => {
        if (!view.model.isElement()) return;

        setDescription(() => {
            const nodeData: NodeType = view.model.get("nodeData");
            return nodeData?.additionalFields?.description;
        });
        setAnchorEl({
            getBoundingClientRect: () => getBoundingClientRectFromView(view),
            nodeType: Node["ELEMENT_NODE"],
        });
        setOpen(true);
    }, []);

    const leaveTimeoutRef = useRef<NodeJS.Timeout>();
    const enterTimeoutRef = useRef<NodeJS.Timeout>();

    const stopLeaveTimer = useCallback(() => {
        clearTimeout(leaveTimeoutRef.current);
    }, []);

    const stopEnterTimer = useCallback(() => {
        clearTimeout(enterTimeoutRef.current);
    }, []);

    const startLeaveTimer = useCallback(() => {
        stopEnterTimer();
        stopLeaveTimer();
        leaveTimeoutRef.current = setTimeout(() => {
            hideDescription();
        }, leaveTimeout);
    }, [hideDescription, leaveTimeout, stopEnterTimer, stopLeaveTimer]);

    const startEnterTimer = useCallback(
        (view: dia.CellView) => {
            stopEnterTimer();
            enterTimeoutRef.current = setTimeout(() => {
                stopLeaveTimer();
                showDescription(view);
            }, enterTimeout);

            view.once(Events.CELL_MOUSELEAVE, () => {
                startLeaveTimer();
                stopEnterTimer();
            });
        },
        [enterTimeout, showDescription, startLeaveTimer, stopEnterTimer, stopLeaveTimer],
    );

    useEffect(() => {
        const graph = graphRef.current;
        graph.processGraphPaper.on(Events.CELL_MOUSEENTER, startEnterTimer);
        return () => {
            graph.processGraphPaper.off(Events.CELL_MOUSEENTER, startEnterTimer);
        };
    }, [startEnterTimer, graphRef]);

    return (
        <Popper
            open={open}
            anchorEl={anchorEl}
            sx={{
                zIndex: (t) => t.zIndex.tooltip,
            }}
            transition
        >
            {({ TransitionProps }) => (
                <Grow {...TransitionProps} in={TransitionProps.in && !!description}>
                    <Paper elevation={5} onMouseLeave={startLeaveTimer} onMouseEnter={stopLeaveTimer}>
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
