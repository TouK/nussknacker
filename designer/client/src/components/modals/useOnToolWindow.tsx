import { useWindowContext } from "@touk/window-manager";
import { useCallback, useEffect } from "react";

import { nodeDetailsClosed, nodeDetailsOpened } from "../../actions/nk/nodeDetails";
import { toolClosed, ToolId, toolOpened } from "../../actions/nk/toolWindow";
import { removeHistorySnapshot, takeHistorySnapshot } from "../../reducers/graph/historySquash";
import { useAppDispatch } from "../../store/storeHelpers";

export function useOnToolWindow(toolId: ToolId, nodeId?: string) {
    const dispatch = useAppDispatch();
    const { data } = useWindowContext();

    const onOpen = useCallback(() => {
        if (toolId === ToolId.node) {
            dispatch(nodeDetailsOpened(nodeId, data.id));
            return () => {
                dispatch(nodeDetailsClosed(nodeId, data.id));
            };
        }

        dispatch(toolOpened(toolId, data.id));
        return () => {
            dispatch(toolClosed(toolId, data.id));
        };
    }, [data.id, dispatch, nodeId, toolId]);

    useEffect(() => {
        return onOpen();
    }, [onOpen]);

    useEffect(() => {
        dispatch(takeHistorySnapshot());
        return () => {
            dispatch(removeHistorySnapshot());
        };
    }, [dispatch]);
}
