import { useWindowContext } from "@touk/window-manager";
import { useCallback, useEffect } from "react";

import type { ToolId } from "../../actions/nk/toolWindow";
import { toolClosed, toolOpened } from "../../actions/nk/toolWindow";
import { removeHistorySnapshot, takeHistorySnapshot } from "../../reducers/graph/historySquash";
import { useAppDispatch } from "../../store/storeHelpers";

export function useOnToolWindow(toolId: ToolId) {
    const dispatch = useAppDispatch();
    const { data } = useWindowContext();

    const onOpen = useCallback(
        (toolId: ToolId) => {
            dispatch(toolOpened(toolId, data.id));
            return () => {
                dispatch(toolClosed(toolId, data.id));
            };
        },
        [data.id, dispatch],
    );

    useEffect(() => {
        return onOpen(toolId);
    }, [onOpen, toolId]);

    useEffect(() => {
        dispatch(takeHistorySnapshot());
        return () => {
            dispatch(removeHistorySnapshot());
        };
    }, [dispatch]);
}
