import { css } from "@emotion/css";
import { WindowKind } from "./WindowKind";
import { Theme } from "@mui/material";

export function getWindowColors(type = WindowKind.default, theme: Theme): string {
    switch (type) {
        case WindowKind.calculateCounts:
        case WindowKind.compareVersions:
            return css(theme.palette.custom.windows.compareVersions);
        case WindowKind.customAction:
            return css(theme.palette.custom.windows.customAction);
        case WindowKind.addProcess:
        case WindowKind.addFragment:
        case WindowKind.default:
        default:
            return css(theme.palette.custom.windows.default);
    }
}