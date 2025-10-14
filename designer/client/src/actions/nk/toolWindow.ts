import { parseWindowsQueryParams, replaceSearchQuery } from "../../containers/hooks/useSearchQuery";
import type { ThunkAction } from "../reduxTypes";

export enum ToolId {
    properties = "properties",
}

export type ToolWindowActions =
    | { type: "TOOL_OPENED"; toolId: ToolId; windowId: string }
    | { type: "TOOL_CLOSED"; toolId: ToolId; windowId: string };

function mergeQuery(changes: Record<string, string[]>) {
    return replaceSearchQuery((current) => ({ ...current, ...changes }));
}

export function toolOpened(toolId: ToolId, windowId: string): ThunkAction {
    return (dispatch) => {
        dispatch({
            type: "TOOL_OPENED",
            toolId,
            windowId,
        });
        mergeQuery(parseWindowsQueryParams({ tool: toolId }));
    };
}

export function toolClosed(toolId: ToolId, windowId: string): ThunkAction {
    return (dispatch) => {
        dispatch({
            type: "TOOL_CLOSED",
            toolId,
            windowId,
        });
        mergeQuery(parseWindowsQueryParams({}, { tool: toolId }));
    };
}
