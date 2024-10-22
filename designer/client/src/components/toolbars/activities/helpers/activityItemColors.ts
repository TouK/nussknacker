import { Theme } from "@mui/material";
import { blend } from "@mui/system";
import { blendLighten, getBorderColor } from "../../../../containers/theme/helpers";

const defaultBorder = (theme: Theme) => `0.5px solid ${getBorderColor(theme)}`;
const activeFoundBorder = (theme: Theme) => `0.5px solid ${blendLighten(theme.palette.primary.main, 0.7)}`;
const foundBorder = (theme: Theme) => `0.5px solid ${blendLighten(theme.palette.primary.main, 0.4)}`;

const runningActiveFoundHeaderBackground = (theme: Theme) => blend(theme.palette.background.paper, theme.palette.primary.main, 0.3);
const highlightedHeaderBackground = (theme: Theme) => blend(theme.palette.background.paper, theme.palette.primary.main, 0.05);
const highlightedActiveFoundHeaderBackground = (theme: Theme) => blend(theme.palette.background.paper, theme.palette.primary.main, 0.2);
const runningHeaderBackground = (theme: Theme) => blend(theme.palette.background.paper, theme.palette.primary.main, 0.2);
const activeFoundItemBackground = (theme: Theme) => blend(theme.palette.background.paper, theme.palette.primary.main, 0.27);
const foundItemBackground = (theme: Theme) => blend(theme.palette.background.paper, theme.palette.primary.main, 0.08);

export const getHeaderColors = (theme: Theme, isHighlighted: boolean, isRunning: boolean, isActiveFound: boolean) => {
    if (isRunning && isActiveFound) {
        return {
            backgroundColor: runningActiveFoundHeaderBackground(theme),
            border: activeFoundBorder(theme),
        };
    }

    if (isHighlighted && isActiveFound) {
        return {
            backgroundColor: highlightedActiveFoundHeaderBackground(theme),
            border: foundBorder(theme),
        };
    }

    if (isRunning) {
        return {
            backgroundColor: runningHeaderBackground(theme),
            border: defaultBorder(theme),
        };
    }

    if (isHighlighted) {
        return {
            backgroundColor: highlightedHeaderBackground(theme),
            border: defaultBorder(theme),
        };
    }

    return {
        backgroundColor: undefined,
        border: "none",
    };
};

export const getItemColors = (theme: Theme, isActiveFound: boolean, isFound: boolean) => {
    if (isActiveFound) {
        return {
            backgroundColor: activeFoundItemBackground(theme),
            border: activeFoundBorder(theme),
        };
    }

    if (isFound) {
        return {
            backgroundColor: foundItemBackground(theme),
            border: foundBorder(theme),
        };
    }

    return {
        backgroundColor: undefined,
        border: "none",
    };
};
