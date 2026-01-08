import { useWindowContext } from "@touk/window-manager";
import { useCallback, useEffect, useLayoutEffect, useRef } from "react";

import { nodeDetailsClosed, nodeDetailsOpened } from "../../actions/nk/nodeDetails";
import { toolClosed, ToolId, toolOpened } from "../../actions/nk/toolWindow";
import { useAppDispatch } from "../../store/storeHelpers";

export function useOnToolWindow(toolId: ToolId, nodeId?: string) {
    const dispatch = useAppDispatch();
    const { data } = useWindowContext();

    const nodeIdRef = useRef(nodeId);
    useLayoutEffect(() => {
        nodeIdRef.current = nodeId;
    }, [nodeId]);

    const onOpen = useCallback(() => {
        if (toolId === ToolId.node) {
            dispatch(nodeDetailsOpened(nodeId, data.id));
            return () => {
                dispatch(nodeDetailsClosed(nodeId, data.id, nodeId !== nodeIdRef.current));
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
}
