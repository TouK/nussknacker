import type { Action } from "../reduxTypes";

export type SwitchToolTipsHighlightAction = {
    type: "SWITCH_TOOL_TIPS_HIGHLIGHT";
    isHighlighted: boolean;
};

export function enableToolTipsHighlight(): Action {
    return {
        type: "SWITCH_TOOL_TIPS_HIGHLIGHT",
        isHighlighted: true,
    };
}

export function disableToolTipsHighlight(): Action {
    return {
        type: "SWITCH_TOOL_TIPS_HIGHLIGHT",
        isHighlighted: false,
    };
}
