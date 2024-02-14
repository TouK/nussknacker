import { DynamicTabData } from "../../containers/DynamicTab";
import { NavLink } from "react-router-dom";
import React from "react";
import { Typography } from "@mui/material";

export function TabElement({ tab, className }: { tab: DynamicTabData; className?: string }): JSX.Element {
    const { id, type, url, title } = tab;
    switch (type) {
        case "Local":
            return (
                <Typography component={NavLink} className={className} to={url}>
                    {title}
                </Typography>
            );
        case "Url":
            return (
                <Typography component={"a"} className={className} href={url} target={"_blank"} rel="noreferrer">
                    {title}
                </Typography>
            );
        default:
            return (
                <Typography component={NavLink} className={className} to={`/${id}`}>
                    {title}
                </Typography>
            );
    }
}
