import { GridEvents, GridRenderCellParams } from "@mui/x-data-grid";
import { KeyboardEventHandler, useCallback } from "react";

const isArrowKey = (key: string): boolean => ["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight"].includes(key);

export function useCellArrowKeys(props: GridRenderCellParams): KeyboardEventHandler<HTMLElement> {
    const { api, field, id } = props;
    return useCallback<KeyboardEventHandler<HTMLElement>>(
        (event) => {
            if (isArrowKey(event.key)) {
                // Get the most recent params because the cell mode may have changed by another listener
                const cellParams = api.getCellParams(id, field);
                api.publishEvent(GridEvents.cellNavigationKeyDown, cellParams, event);
            }
        },
        [api, field, id],
    );
}
