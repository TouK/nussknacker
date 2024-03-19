import { DataEditorRef, DrawCellCallback, GridSelection } from "@glideapps/glide-data-grid";
import type { GridMouseEventArgs } from "@glideapps/glide-data-grid/src/internal/data-grid/event-args";
import type { Highlight } from "@glideapps/glide-data-grid/src/internal/data-grid/render/data-grid-render.cells";
import { alpha, styled, Tooltip, tooltipClasses, TooltipProps, useTheme } from "@mui/material";
import React, { useCallback, useMemo, useRef, useState } from "react";
import { FieldError } from "../../Validators";
import { DataColumn } from "./state/tableState";

// it's easier to fake children than creating something new
const StyledTooltip = styled(({ className, ...props }: Omit<TooltipProps, "children">) => (
    <Tooltip {...props} classes={{ popper: className }}>
        <span />
    </Tooltip>
))(({ theme, color = theme.palette.error.main }) => {
    return {
        [`& .${tooltipClasses.tooltip}`]: {
            backgroundColor: color,
            color: theme.palette.getContrastText(color),
        },
        [`& .${tooltipClasses.arrow}`]: {
            color: color,
        },
    };
});

export function useErrorHighlights(fieldErrors: FieldError[], columns: DataColumn[], tableRef: React.MutableRefObject<DataEditorRef>) {
    const { palette } = useTheme();
    const positionRef = useRef<DOMRectInit>();
    const [tooltipMessage, setTooltipMessage] = useState<string>(null);
    const [tooltipOpen, setTooltipOpen] = useState<boolean>(null);

    const cellErrors = useMemo(
        () =>
            fieldErrors.flatMap((e) =>
                e.details?.cellErrors
                    ?.map(({ rowIndex, ...e }) => ({
                        ...e,
                        x: columns.findIndex((c) => c.name === e.columnName),
                        y: rowIndex,
                    }))
                    .filter((e) => e?.x >= 0),
            ),
        [columns, fieldErrors],
    );

    const getErrorForCell = useCallback(
        ([col, row]: readonly [number, number] = [-1, -1]) => cellErrors.find(({ x, y }) => x === col && y === row),
        [cellErrors],
    );

    const getSelectedCell = useCallback(
        (selection: GridMouseEventArgs | GridSelection) => {
            if ("location" in selection) {
                return {
                    cell: selection.location,
                    rect: selection.kind === "cell" ? selection.bounds : null,
                };
            }
            if (selection.current) {
                const { range, cell } = selection.current;
                const { height, width } = range;
                return {
                    cell,
                    rect: width === 1 && height === 1 ? tableRef.current.getBounds(cell[0], cell[1]) : null,
                };
            }
        },
        [tableRef],
    );

    const toggleTooltip = useCallback(
        (selection: GridMouseEventArgs | GridSelection) => {
            const { cell, rect } = getSelectedCell(selection);
            const error = getErrorForCell(cell);
            setTooltipOpen(() => {
                if (rect && error) {
                    positionRef.current = rect;
                    setTooltipMessage(error.errorMessage);
                    return true;
                } else {
                    return false;
                }
            });
        },
        [getErrorForCell, getSelectedCell],
    );

    const highlightRegions = useCallback(
        (): readonly Highlight[] =>
            cellErrors.map(({ x, y }) => ({
                range: {
                    height: 1,
                    width: 1,
                    x,
                    y,
                },
                color: alpha(palette.error.main, 0.05),
                style: "dashed",
            })),
        [cellErrors, palette.error.main],
    );

    const drawCell: DrawCellCallback = React.useCallback(
        (args, draw) => {
            draw(); // draw up front to draw over the cell
            const { ctx, rect, row, col } = args;
            if (getErrorForCell([col, row])) {
                const size = 7;
                ctx.beginPath();
                ctx.moveTo(rect.x + rect.width - size, rect.y + 1);
                ctx.lineTo(rect.x + rect.width, rect.y + size + 1);
                ctx.lineTo(rect.x + rect.width, rect.y + 1);
                ctx.closePath();
                ctx.save();
                ctx.fillStyle = palette.error.main;
                ctx.fill();
                ctx.restore();
            }
        },
        [getErrorForCell, palette.error.main],
    );

    const tooltipElement = (
        <StyledTooltip
            arrow
            open={tooltipOpen}
            title={tooltipMessage}
            color={palette.error.dark}
            placement="top"
            PopperProps={{
                anchorEl: {
                    getBoundingClientRect: () => DOMRect.fromRect(positionRef.current),
                },
            }}
        />
    );
    return {
        toggleTooltip,
        highlightRegions,
        drawCell,
        tooltipElement,
    };
}
