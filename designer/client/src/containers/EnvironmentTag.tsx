import { useSelector } from "react-redux";
import { getEnvironmentAlert } from "../reducers/selectors/settings";
import React from "react";
import { styled, Typography } from "@mui/material";

// TODO: get rid of 'indicator-', maybe rename to "warn", "prod" etc.
export enum EnvironmentTagColor {
    green = "indicator-green",
    red = "indicator-red",
    blue = "indicator-blue",
    yellow = "indicator-yellow",
}

const Tag = styled(Typography)(({ theme }) => ({
    backgroundColor: theme.palette.primary.main,
    whiteSpace: "nowrap",
    borderRadius: "3px",
    overflow: "hidden",
    textOverflow: "ellipsis",
    color: theme.palette.getContrastText(theme.palette.primary.main),
}));

export function EnvironmentTag() {
    //TODO: We don't need a color, since it will be always primary color
    const { content } = useSelector(getEnvironmentAlert);

    if (!content) {
        return null;
    }

    return (
        <Tag variant={"body2"} px={1} py={0.5} title={content}>
            {content}
        </Tag>
    );
}
