import { styled } from "@mui/material";
import React from "react";
import { NavLink } from "react-router-dom";

import type { DynamicTabData } from "../../containers/DynamicTab";

function UnstyledTabElement({ tab, ...props }: { tab: DynamicTabData; className?: string }): JSX.Element {
    const { id, type, url, title } = tab;
    switch (type) {
        case "Local":
            return (
                <NavLink to={url} {...props}>
                    {title}
                </NavLink>
            );
        case "Url":
            return (
                <a href={url} target={"_blank"} rel="noreferrer" {...props}>
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
