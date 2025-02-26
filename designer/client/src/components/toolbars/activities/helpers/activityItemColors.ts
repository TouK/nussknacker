import { Theme } from "@mui/material";
import { blend } from "@mui/system";
import { getBorderColor } from "../../../../containers/theme/helpers";

const defaultBorder = (theme: Theme) => `0.5px solid ${getBorderColor(theme)}`;
const activeBorder = (theme: Theme) => `0.5px solid ${blend(theme.palette.background.paper, theme.palette.primary.main, 0.4)}`;
const deployedBorder = (theme: Theme) => `0.5px solid ${theme.palette.primary.main}`;
const selectedVersionBorder = (theme: Theme) => `0.5px solid ${theme.palette.primary.main}`;

const runningActiveFoundHeaderBackground = (theme: Theme) => blend(theme.palette.background.paper, theme.palette.primary.main, 0.3);
const highlightedHeaderBackground = (theme: Theme) => blend(theme.palette.background.paper, theme.palette.primary.main, 0.05);
const highlightedActiveFoundHeaderBackground = (theme: Theme) => blend(theme.palette.background.paper, theme.palette.primary.main, 0.2);
const runningHeaderBackground = (theme: Theme) => blend(theme.palette.background.paper, theme.palette.primary.main, 0.2);
const activeFoundItemBackground = (theme: Theme) => blend(theme.palette.background.paper, theme.palette.primary.main, 0.2);
const foundItemBackground = (theme: Theme) => blend(theme.palette.background.paper, theme.palette.primary.main, 0.08);
const selectedVersionHeaderBackground = (theme: Theme) => blend(theme.palette.background.paper, theme.palette.primary.main, 0.2);

export const getHeaderColors = (
    theme: Theme,
    isHighlighted: boolean,
    isDeploymentActive: boolean,
    isActiveFound: boolean,
    isVersionSelected: boolean,
) => {
    if (isDeploymentActive && isActiveFound) {
        return {
            backgroundColor: runningActiveFoundHeaderBackground(theme),
            border: activeBorder(theme),
        };
    }

    if (isHighlighted && isActiveFound) {
        return {
            backgroundColor: highlightedActiveFoundHeaderBackground(theme),
            border: activeBorder(theme),
        };
    }

    if (isDeploymentActive) {
        return {
            backgroundColor: runningHeaderBackground(theme),
            border: deployedBorder(theme),
        };
    }

    if (isHighlighted) {
        return {
            backgroundColor: highlightedHeaderBackground(theme),
            border: defaultBorder(theme),
        };
    }

    if (isVersionSelected) {
        return {
            backgroundColor: selectedVersionHeaderBackground(theme),
            border: selectedVersionBorder(theme),
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
            border: activeBorder(theme),
        };
    }

    if (isFound) {
        return {
            backgroundColor: foundItemBackground(theme),
            border: activeBorder(theme),
        };
    }

    return {
        backgroundColor: undefined,
        border: "none",
    };
};
