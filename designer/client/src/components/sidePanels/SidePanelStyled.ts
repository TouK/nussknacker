import { styled, Theme } from "@mui/material";
import { alpha } from "../../containers/theme/helpers";
import { PanelSide } from "./SidePanel";
import { css } from "@emotion/css";

const SCROLL_THUMB_SIZE = 8;
const SIDEBAR_WIDTH = 290;
const PANEL_WIDTH = SIDEBAR_WIDTH + SCROLL_THUMB_SIZE;
const TOOLBARS_GAP = "3px";

function checkLeftSide(props: ScrollToggle) {
    if (props.side === PanelSide.Left) {
        return props.isOpened ? 0 : -PANEL_WIDTH;
    }
}

function checkRightSide(props: ScrollToggle) {
    if (props.side === PanelSide.Right) {
        return props.isOpened ? 0 : -PANEL_WIDTH;
    }
}

interface ScrollToggle {
    side: PanelSide;
    isOpened?: boolean;
}

export const StyledScrollToggleChild = styled("div")((props: ScrollToggle) => ({
    width: PANEL_WIDTH,
    boxSizing: "border-box",
    minHeight: "100%",
    display: "flex",
    flexDirection: "column",
    pointerEvents: "none",
    alignItems: props.side === PanelSide.Left ? "flex-start" : "flex-end",
}));

export const StyledScrollToggle = styled("div")((props: ScrollToggle) => ({
    pointerEvents: "none",
    userSelect: "none",
    width: PANEL_WIDTH,
    transition: "left 0.5s ease, right 0.5s ease",
    position: "absolute",
    zIndex: 1,
    top: 0,
    bottom: 0,
    overflow: "hidden",
    left: checkLeftSide(props),
    right: checkRightSide(props),
}));

export const styledScrollTogglePanelWrapper = (theme: Theme) => css`
    .droppable {
        min-width: 1px;
        display: flex;
        flex-direction: column;
        flex-grow: 1;
        justify-content: space-around;
    }

    .droppable:first-child {
        justify-content: flex-start;
    }

    .droppable:last-child {
        justify-content: flex-end;
    }

    .draggable-list {
        display: flex;
        flex-direction: column;
    }

    .draggable-list > .background {
        position: relative;
        transition: all 0.3s;
        min-height: 0;
        display: flex;
        flex-direction: column;
    }

    .is-dragging-started .draggable-list > .background,
    .is-dragging-from > .draggable-list > .background,
    .is-dragging-over > .draggable-list > .background {
        min-height: 3em;
        min-width: 3em;

        &::after {
            transition: all 0.3s;
            content: "";
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            backdrop-filter: blur(0.5px);
            background: ${alpha(theme.custom.colors.primaryColor, 0.2)};
            outline: 3px dashed white;
            outline-offset: -4px;
        }
    }

    .is-dragging-from > .draggable-list > .background {
        &::after {
            background: ${alpha(theme.custom.colors.yellow, 0.2)};
            outline-color: ${theme.custom.colors.yellow};
        }
    }

    .is-dragging-over > .draggable-list > .background {
        &::after {
            background: ${alpha(theme.custom.colors.deepskyblue, 0.2)};
            outline-color: ${theme.custom.colors.deepskyblue};
        }
    }

    .draggable {
        background-color: ${theme.palette.background.default};

        &.is-dragging:focus {
            outline: none;
        }

        &:focus {
            outline: 4px solid ${theme.custom.colors.deepskyblue};
            outline-offset: -4px;
        }

        .background {
            position: relative;
            box-sizing: border-box;
            overflow: hidden;
        }
    }

    .left {
        .draggable .background {
            border-bottom-right-radius: 3px;
            border-top-right-radius: 3px;
        }
    }

    .right {
        .draggable .background {
            border-bottom-left-radius: 3px;
            border-top-left-radius: 3px;
        }
    }

    .draggable {
        padding: calc(${TOOLBARS_GAP} / 2) 0;

        &:first-child {
            padding-top: 0;
        }

        &:last-child {
            padding-bottom: 0;
        }
    }

    .droppable {
        padding: calc(${TOOLBARS_GAP} / 2) 0;

        &.top {
            padding-top: 0;
        }

        &.bottom {
            padding-bottom: 0;
        }
    }

    .left .draggable-list > .background {
        align-items: flex-start;
    }

    .right .draggable-list > .background {
        align-items: flex-end;
    }
`;
