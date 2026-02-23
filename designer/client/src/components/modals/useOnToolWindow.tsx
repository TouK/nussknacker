import { useWindowContext } from "@touk/window-manager";
import { useCallback, useEffect, useLayoutEffect, useRef } from "react";

import { nodeDetailsClosed, nodeDetailsOpened } from "../../actions/nk/nodeDetails";
import { toolClosed, ToolId, toolOpened } from "../../actions/nk/toolWindow";
import { useAppDispatch } from "../../store/storeHelpers";

export function useOnNodeWindow(nodeId: string) {
    const dispatch = useAppDispatch();
    const { data } = useWindowContext();

    const nodeIdRef = useRef(nodeId);
    useLayoutEffect(() => {
        nodeIdRef.current = nodeId;
    }, [nodeId]);

    const onOpen = useCallback(() => {
        dispatch(nodeDetailsOpened(nodeId, data.id));
        return () => {
            dispatch(nodeDetailsClosed(nodeId, data.id, nodeId !== nodeIdRef.current));
        };
    }, [data.id, dispatch, nodeId]);

    useEffect(() => {
        return onOpen();
    }, [onOpen]);
}

export function useOnPropertiesWindow() {
    const dispatch = useAppDispatch();
    const { data } = useWindowContext();

    const onOpen = useCallback(() => {
        dispatch(toolOpened(ToolId.properties, data.id));
        return () => {
            dispatch(toolClosed(ToolId.properties, data.id));
        };
    }, [data.id, dispatch]);

    useEffect(() => {
        return onOpen();
    }, [onOpen]);
}
