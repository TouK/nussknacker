import React, { PropsWithChildren, useCallback, useState } from "react";
import { ToolbarPosition } from "../../actions/nk/toolbars";
import { DragDropContext, DropResult } from "@hello-pangea/dnd";
import { alpha, GlobalStyles, useTheme } from "@mui/material";
import { DRAGGABLE_LIST_CLASSNAME, DRAGGING_FROM_CLASSNAME, DRAGGING_OVER_CLASSNAME } from "./ToolbarsContainer";
import { SIDEBAR_WIDTH } from "../../stylesheets/variables";

type Props = PropsWithChildren<{
    onMove: (from: ToolbarPosition, to: ToolbarPosition) => void;
}>;

export const TOOLBAR_DRAGGABLE_TYPE = "TOOLBAR";

export function DragAndDropContainer({ children, onMove }: Props) {
    const [isDragging, setIsDragging] = useState(false);

    const onDragEnd = useCallback(
        (result: DropResult) => {
            setIsDragging(false);
            const { destination, type, reason, source } = result;
            if (reason === "DROP" && type === TOOLBAR_DRAGGABLE_TYPE && destination) {
                const from: ToolbarPosition = [source.droppableId, source.index];
                const to: ToolbarPosition = [destination.droppableId, destination.index];
                onMove(from, to);
            }
        },
        [onMove],
    );

    const onDragStart = useCallback(() => {
        setIsDragging(true);
    }, []);

    const theme = useTheme();

    return (
        <DragDropContext onDragEnd={onDragEnd} onDragStart={onDragStart}>
            <GlobalStyles
                styles={{
                    [`.${DRAGGABLE_LIST_CLASSNAME}`]: {
                        minHeight: isDragging ? "1em" : null,
                        minWidth: SIDEBAR_WIDTH,
                        position: "relative",
                        backgroundColor: theme.palette.background.paper,
                        "&::after": {
                            content: isDragging ? "''" : null,
                            transition: theme.transitions.create(["all"], { duration: theme.transitions.duration.standard }),
                            position: "absolute",
                            top: 0,
                            left: 0,
                            right: 0,
                            bottom: 0,
                            backdropFilter: "blur(0.5px)",
                            background: theme.palette.action.selected,
                            outline: `3px dashed ${theme.palette.common.white}`,
                            outlineOffset: -4,

                            [`.${DRAGGING_FROM_CLASSNAME} > &`]: {
                                background: alpha(theme.palette.warning.light, 0.2),
                                outlineColor: theme.palette.warning.light,
                            },

                            [`.${DRAGGING_OVER_CLASSNAME} > &`]: {
                                background: alpha(theme.palette.info.main, 0.2),
                                outlineColor: theme.palette.info.main,
                            },
                        },
                    },
                }}
            />
            {children}
        </DragDropContext>
    );
}
