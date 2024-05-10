import { DynamicTabData } from "../../containers/DynamicTab";
import { NavLink } from "react-router-dom";
import React from "react";
import { Typography, TypographyProps } from "@mui/material";

interface Props extends TypographyProps {
    tab: DynamicTabData;
    className?: string;
}

export function TabElement({ tab, className, ...props }: Props): JSX.Element {
    const { id, type, url, title } = tab;
    switch (type) {
        case "Local":
            return (
                <Typography component={NavLink} className={className} to={url} {...props}>
                    {title}
                </Typography>
            );
        case "Url":
            return (
                <Typography component={"a"} className={className} href={url} target={"_blank"} rel="noreferrer" {...props}>
                    {title}
                </Typography>
            );
        default:
            return (
                <Typography component={NavLink} className={className} to={`/${id}`} {...props}>
                    {title}
                </Typography>
            );
    }
}
