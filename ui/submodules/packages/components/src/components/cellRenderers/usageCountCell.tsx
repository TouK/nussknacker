import React, { KeyboardEventHandler, useCallback } from "react";
import { GridEvents, GridRenderCellParams, GridRowId, useGridApiContext } from "@mui/x-data-grid";
import { Box, Link } from "@mui/material";
import { Link as RouterLink } from "react-router-dom";
import { Link as LinkIcon } from "@mui/icons-material";

const isArrowKey = (key: string): boolean => ["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight"].includes(key);

export function useCellArrowKeys(cellId: GridRowId, cellField: string): KeyboardEventHandler {
    const apiRef = useGridApiContext();
    return useCallback<KeyboardEventHandler>(
        (event) => {
            if (isArrowKey(event.key)) {
                // Get the most recent params because the cell mode may have changed by another listener
                const cellParams = apiRef.current.getCellParams(cellId, cellField);
                apiRef.current.publishEvent(GridEvents.cellNavigationKeyDown, cellParams, event);
            }
        },
        [apiRef, cellField, cellId],
    );
}

export function UsageCountCell({ value, row, id, field }: GridRenderCellParams): JSX.Element {
    const handleCellKeyDown = useCellArrowKeys(id, field);

    if (!value) {
        return <Box sx={{ fontWeight: "light", padding: "0 10px", opacity: 0.25 }}>{value}</Box>;
    }
    return (
        <Link
            sx={{
                fontWeight: "bold",
                padding: "0 10px",
                flex: 1,
                ":focus": {
                    outline: "unset",
                },
            }}
            component={RouterLink}
            to={`/${row.name}`}
            tabIndex={0}
            onKeyDown={handleCellKeyDown}
        >
            <Box sx={{ display: "flex", alignItems: "center", justifyContent: "flex-end" }}>
                <LinkIcon
                    sx={{
                        height: ".75em",
                        margin: ".25em",
                        opacity: 0.1,
                        "a:hover &": {
                            opacity: 0.5,
                        },
                        "a:focus &": {
                            opacity: 0.5,
                        },
                    }}
                />
                {value}
            </Box>
        </Link>
    );
}
