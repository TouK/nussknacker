import { css, cx } from "@emotion/css";
import React from "react";
import { BORDER_RADIUS, CONTENT_PADDING, iconBackgroundSize, iconSize } from "./graph/EspNode/esp";
import { PreloadedIcon, stickyNoteIconSrc } from "./toolbars/creator/ComponentIcon";
import { alpha, useTheme } from "@mui/material";
import { getBorderColor, getStickyNoteBackgroundColor } from "../containers/theme/helpers";
import { STICKY_NOTE_CONSTRAINTS, STICKY_NOTE_DEFAULT_COLOR } from "./graph/EspNode/stickyNote";

const PREVIEW_SCALE = 0.9;
const ACTIVE_ROTATION = 2;
const INACTIVE_SCALE = 1.5;

export function StickyNotePreview({ isActive, isOver }: { isActive?: boolean; isOver?: boolean }): JSX.Element {
    const theme = useTheme();
    const scale = isOver ? 1 : PREVIEW_SCALE;
    const rotation = isActive ? (isOver ? -ACTIVE_ROTATION : ACTIVE_ROTATION) : 0;
    const finalScale = isActive ? 1 : INACTIVE_SCALE;

    const nodeStyles = css({
        position: "relative",
        width: STICKY_NOTE_CONSTRAINTS.DEFAULT_WIDTH,
        height: STICKY_NOTE_CONSTRAINTS.DEFAULT_HEIGHT,
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

    const colors = css({
        opacity: 0.5,
        borderColor: getBorderColor(theme),
        backgroundColor: getStickyNoteBackgroundColor(theme, STICKY_NOTE_DEFAULT_COLOR).main,
    });

    const imageStyles = css({
        padding: iconSize / 2 - CONTENT_PADDING / 2,
        margin: CONTENT_PADDING / 2,
        borderRadius: BORDER_RADIUS,
        width: iconBackgroundSize / 2,
        height: iconBackgroundSize / 2,
        color: theme.palette.common.black,
        "> svg": {
            height: iconSize,
            width: iconSize,
        },
    });

    return (
        <div className={cx(colors, nodeStyles)}>
            <div className={cx(imageStyles, colors)}>
                <PreloadedIcon src={stickyNoteIconSrc} />
            </div>
        </div>
    );
}
