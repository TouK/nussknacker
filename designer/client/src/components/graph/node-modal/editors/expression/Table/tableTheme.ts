import { DataEditorProps } from "@glideapps/glide-data-grid";
import { alpha, darken, getLuminance, lighten, useTheme } from "@mui/material";
import { blendDarken, blendLighten, getBorderColor } from "../../../../../../containers/theme/helpers";

export const useTableTheme = (): DataEditorProps["theme"] => {
    const theme = useTheme();
    const bgCell = theme.palette.background.paper;
    const text = theme.palette.getContrastText(bgCell);
    const bgIconHeader = text;
    const textHeader = getLuminance(text) > 0.5 ? darken(text, 0.2) : lighten(text, 0.2);
    const bgHeader = blendDarken(bgCell, 0.15);
    const bgCellMedium = getLuminance(bgCell) > 0.5 ? darken(bgCell, 0.1) : lighten(bgCell, 0.1);
    const accentColor = theme.palette.primary.main;
    return {
        accentColor,
        accentFg: theme.palette.getContrastText(accentColor),
        accentLight: alpha(accentColor, 0.1),

        textDark: text,
        textMedium: getLuminance(text) > 0.5 ? darken(text, 0.2) : lighten(text, 0.2),
        textLight: getLuminance(text) > 0.5 ? darken(text, 0.4) : lighten(text, 0.4),

        bgIconHeader,
        fgIconHeader: getLuminance(bgIconHeader) > 0.5 ? darken(bgIconHeader, 0.4) : lighten(bgIconHeader, 0.4),
        textHeader,
        textGroupHeader: textHeader,
        textHeaderSelected: theme.palette.getContrastText(accentColor),

        bgCell,
        bgCellMedium,
        bgHeader,
        bgHeaderHasFocus: bgHeader,
        bgHeaderHovered: bgHeader,

        borderColor: getBorderColor(theme),

        headerFontStyle: "700 13px",
        baseFontStyle: "300 13px",
        editorFontSize: "13px",
        fontFamily: theme.typography.fontFamily,
    };
};
