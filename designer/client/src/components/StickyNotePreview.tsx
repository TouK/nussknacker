import { css, cx } from "@emotion/css";
import React from "react";
import { NodeType } from "../types";
import { BORDER_RADIUS, CONTENT_PADDING, iconBackgroundSize, iconSize } from "./graph/EspNode/esp";
import { ComponentIcon } from "./toolbars/creator/ComponentIcon";
import { alpha, useTheme } from "@mui/material";
import { blend } from "@mui/system";
import { getBorderColor, getStickyNoteBackgroundColor } from "../containers/theme/helpers";
import { STICKY_NOTE_DEFAULT_COLOR, STICKY_NOTE_HEIGHT, STICKY_NOTE_WIDTH } from "./graph/EspNode/stickyNote";

export function StickyNotePreview({ node, isActive, isOver }: { node: NodeType; isActive?: boolean; isOver?: boolean }): JSX.Element {
    const theme = useTheme();

    const PREVIEW_SCALE = 0.9;
    const ACTIVE_ROTATION = 2;
    const INACTIVE_SCALE = 1.5;
    const scale = isOver ? 1 : PREVIEW_SCALE;
    const rotation = isActive ? (isOver ? -ACTIVE_ROTATION : ACTIVE_ROTATION) : 0;
    const finalScale = isActive ? 1 : INACTIVE_SCALE;

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
        transform: `translate(-80%, -50%) scale(${scale}) rotate(${rotation}deg) scale(${finalScale})`,
        opacity: isActive ? undefined : 0,
        transition: "all .5s, opacity .3s",
        willChange: "transform, opacity, border-color, background-color",
    });

    const nodeColors = css({
        opacity: 0.5,
        borderColor: getBorderColor(theme),
        backgroundColor: getStickyNoteBackgroundColor(theme, STICKY_NOTE_DEFAULT_COLOR).main,
    });
    const nodeColorsHover = css({
        borderColor: blend(getBorderColor(theme), theme.palette.secondary.main, 0.2),
        backgroundColor: getStickyNoteBackgroundColor(theme, STICKY_NOTE_DEFAULT_COLOR).main,
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
        color: theme.palette.common.black,
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
