import { css, cx } from "@emotion/css";
import { alpha, useTheme } from "@mui/material";
import React from "react";

import { useUserSettings } from "../common/useUserSettings";
import { getBorderColor } from "../containers/theme/helpers";
import { BORDER_RADIUS } from "./graph/EspNode/esp";
import {
    getStickyNoteAdvancedBackgroundColor,
    STICKY_NOTE_ADVANCED_CONSTRAINTS,
} from "./graph/EspNode/stickyNote/advancedStickyNoteConfig";
import { getStickyNoteBasicBackgroundColor, STICKY_NOTE_BASIC_CONSTRAINTS } from "./graph/EspNode/stickyNote/basicStickyNoteConfig";

const PREVIEW_SCALE = 0.9;
const ACTIVE_ROTATION = 2;
const INACTIVE_SCALE = 1.5;

export function StickyNotePreview({ isActive, isOver }: { isActive?: boolean; isOver?: boolean }): React.JSX.Element {
    const theme = useTheme();
    const scale = isOver ? 1 : PREVIEW_SCALE;
    const rotation = isActive ? (isOver ? -ACTIVE_ROTATION : ACTIVE_ROTATION) : 0;
    const finalScale = isActive ? 1 : INACTIVE_SCALE;
    const [areAdvancedStickyNotesEnabled] = useUserSettings("node.advancedStickyNotes");

    const { DEFAULT_COLOR, DEFAULT_HEIGHT, DEFAULT_WIDTH } = areAdvancedStickyNotesEnabled
        ? STICKY_NOTE_ADVANCED_CONSTRAINTS
        : STICKY_NOTE_BASIC_CONSTRAINTS;
    const backgroundColor = areAdvancedStickyNotesEnabled
        ? getStickyNoteAdvancedBackgroundColor(theme, DEFAULT_COLOR)
        : getStickyNoteBasicBackgroundColor(theme, DEFAULT_COLOR);

    const nodeStyles = css({
        position: "relative",
        width: DEFAULT_WIDTH,
        height: DEFAULT_HEIGHT,
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
        opacity: 0.8,
        borderColor: getBorderColor(theme),
        backgroundColor: backgroundColor.main,
    });

    return <div className={cx(colors, nodeStyles)}></div>;
}
