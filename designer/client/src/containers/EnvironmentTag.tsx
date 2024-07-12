import { useSelector } from "react-redux";
import { getEnvironmentAlert } from "../reducers/selectors/settings";
import React from "react";
import { styled, Theme, Typography } from "@mui/material";

// TODO: get rid of 'indicator-', maybe rename to "warn", "prod" etc.
export enum EnvironmentTagColor {
    green = "indicator-green",
    red = "indicator-red",
    blue = "indicator-blue",
    yellow = "indicator-yellow",
}

const getBackgroundColor = (color: EnvironmentTagColor, theme: Theme) => {
    switch (color) {
        case EnvironmentTagColor.green:
            return theme.palette.custom.environmentAlert[EnvironmentTagColor.green];
        case EnvironmentTagColor.blue:
            return theme.palette.custom.environmentAlert[EnvironmentTagColor.blue];
        case EnvironmentTagColor.red:
            return theme.palette.custom.environmentAlert[EnvironmentTagColor.red];
        case EnvironmentTagColor.yellow:
            return theme.palette.custom.environmentAlert[EnvironmentTagColor.yellow];
        default:
            return color;
    }
};

const Tag = styled(Typography)<{ color: EnvironmentTagColor }>(({ color, theme }) => {
    const backgroundColor = getBackgroundColor(color, theme);
    return {
        backgroundColor,
        whiteSpace: "nowrap",
        borderRadius: "3px",
        overflow: "hidden",
        textOverflow: "ellipsis",
        color: theme.palette.getContrastText(backgroundColor),
    };
});

export function EnvironmentTag() {
    const { content, color } = useSelector(getEnvironmentAlert);

    if (!content) {
        return null;
    }

    return (
        <Tag variant={"body2"} px={1} color={color} py={0.5} title={content}>
            {content}
        </Tag>
    );
}
