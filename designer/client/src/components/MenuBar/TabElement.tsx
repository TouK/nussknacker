import { styled } from "@mui/material";
import React from "react";
import type { Location } from "react-router-dom";
import { NavLink, useLocation } from "react-router-dom";

import type { DynamicTabData } from "../../containers/DynamicTab";

function UnstyledTabElement({ tab, ...props }: { tab: DynamicTabData; className?: string }): React.JSX.Element {
    const { id, type, title } = tab;
    const tabNavigationUrl = getTabNavigationUrl(tab, useLocation());
    switch (type) {
        case "Local":
            return (
                <NavLink to={tabNavigationUrl} {...props}>
                    {title}
                </NavLink>
            );
        case "Url":
            return (
                <a href={tabNavigationUrl} target={"_blank"} rel="noreferrer" {...props}>
                    {title}
                </a>
            );
        default:
            return (
                <NavLink to={`/${id}`} {...props}>
                    {title}
                </NavLink>
            );
    }
}

export const TabElement = styled(UnstyledTabElement)(({ theme }) => ({
    padding: ".8em 1.2em",
    whiteSpace: "nowrap",

    "&, &:hover, &:focus": {
        color: "inherit",
        textDecoration: "none",
    },

    "&:hover": {
        background: theme.palette.action.hover,
    },

    "&.active": {
        background: theme.palette.action.active,
    },
}));

export function getTabNavigationUrl(tab: DynamicTabData, currentLocation: Location): string {
    const { url, currentLocationInQuery } = tab;

    const fullOriginalPath = currentLocation.pathname + currentLocation.search + currentLocation.hash;
    const fullOriginalUrl = window.location.origin + fullOriginalPath;

    if (!currentLocationInQuery || !currentLocationInQuery.enabled) {
        return url;
    }

    const enrichedUrl = new URL(url);
    enrichedUrl.searchParams.set(currentLocationInQuery.parameterName, fullOriginalUrl);
    return enrichedUrl.toString();
}
