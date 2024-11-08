import { css, cx } from "@emotion/css";
import React from "react";
import { NodeType } from "../types";
import { BORDER_RADIUS, CONTENT_PADDING, iconBackgroundSize, iconSize } from "./graph/EspNode/esp";
import { ComponentIcon } from "./toolbars/creator/ComponentIcon";
import { alpha, styled, useTheme } from "@mui/material";
import { blend } from "@mui/system";
import { blendLighten, getBorderColor, getStickyNoteBackgroundColor } from "../containers/theme/helpers";

export const STICKY_NOTE_WIDTH = 300;
export const STICKY_NOTE_HEIGHT = 250;

export function StickyNotePreview({ node, isActive, isOver }: { node: NodeType; isActive?: boolean; isOver?: boolean }): JSX.Element {
    const theme = useTheme();

    //TODO this is duplicated
    const nodeStyles = css({
        position: "relative",
        width: STICKY_NOTE_WIDTH,
        height: STICKY_NOTE_HEIGHT,
        borderRadius: BORDER_RADIUS,
        boxSizing: "content-box",
        display: "inline-flex",
        filter: `drop-shadow(0 4px 8px ${alpha(theme.palette.common.black, 0.5)})`,
        borderWidth: 0.5,
        borderStyle: "solid",
        transformOrigin: "80% 50%",
        transform: `translate(-80%, -50%) scale(${isOver ? 1 : 0.9}) rotate(${isActive ? (isOver ? -2 : 2) : 0}deg) scale(${
            isActive ? 1 : 1.5
        })`,
        opacity: isActive ? undefined : 0,
        transition: "all .5s, opacity .3s",
        willChange: "transform, opacity, border-color, background-color",
    });

    const nodeColors = css({
        opacity: 0.5,
        borderColor: getBorderColor(theme),
        backgroundColor: blendLighten(getStickyNoteBackgroundColor(theme, ""), 0.04), //TODO pass note color
    });
    const nodeColorsHover = css({
        borderColor: blend(getBorderColor(theme), theme.palette.secondary.main, 0.2),
        backgroundColor: blend(blendLighten(getStickyNoteBackgroundColor(theme, ""), 0.04), theme.palette.secondary.main, 0.2), //TODO pass note color
    });

    const imageStyles = css({
        padding: iconSize / 2 - CONTENT_PADDING / 2,
        margin: CONTENT_PADDING / 2,
        borderRadius: BORDER_RADIUS,
        width: iconBackgroundSize / 2,
        height: iconBackgroundSize / 2,
        "> svg": {
            height: iconSize,
            width: iconSize,
        },
    });

    const imageColors = css({
        background: theme.palette.custom.getNodeStyles(node)?.fill,
        color: theme.palette.common.white,
    });

    const colors = isOver ? nodeColorsHover : nodeColors;
    return (
        <div className={cx(colors, nodeStyles)}>
            <div className={cx(imageStyles, imageColors)}>
                <ComponentIcon node={node} />
            </div>
        </div>
    );
}
