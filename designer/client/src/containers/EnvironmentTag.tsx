import { useSelector } from "react-redux";
import { getEnvironmentAlert } from "../reducers/selectors/settings";
import React, { useMemo } from "react";
import { styled, Typography } from "@mui/material";

// TODO: get rid of 'indicator-', maybe rename to "warn", "prod" etc.
export enum EnvironmentTagColor {
    green = "indicator-green",
    red = "indicator-red",
    blue = "indicator-blue",
    yellow = "indicator-yellow",
}

const Tag = styled(Typography)<{ backgroundColor: string }>(({ backgroundColor }) => ({
    backgroundColor: backgroundColor,
    whiteSpace: "nowrap",
    borderRadius: "3px",
    overflow: "hidden",
    textOverflow: "ellipsis",
    color: "hsl(0,0%,100%)",
}));

export function EnvironmentTag() {
    const { content, color } = useSelector(getEnvironmentAlert);
    const background = useMemo(() => {
        switch (color) {
            case EnvironmentTagColor.green:
                return "hsl(120,39%,54%)";
            case EnvironmentTagColor.blue:
                return "hsl(194,66%,61%)";
            case EnvironmentTagColor.red:
                return "hsl(2,64%,58%)";
            case EnvironmentTagColor.yellow:
                return "hsl(35,84%,62%)";
            default:
                return color;
        }
    }, [color]);

    if (!content) {
        return null;
    }

    return (
        <Tag variant={"body1"} px={1} py={0.5} backgroundColor={background} title={content}>
            {content}
        </Tag>
    );
}
