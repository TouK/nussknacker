import { DynamicTabData } from "../../containers/DynamicTab";
import { NavLink } from "react-router-dom";
import React from "react";

export function TabElement({ tab, className }: { tab: DynamicTabData; className?: string }): JSX.Element {
    const { id, type, url, title } = tab;
    switch (type) {
        case "Local":
            return (
                <NavLink className={className} to={url}>
                    {title}
                </NavLink>
            );
        case "Url":
            return (
                <a className={className} href={url} target={"_blank"} rel="noreferrer">
                    {title}
                </a>
            );
        default:
            return (
                <NavLink className={className} to={`/${id}`}>
                    {title}
                </NavLink>
            );
    }
}
