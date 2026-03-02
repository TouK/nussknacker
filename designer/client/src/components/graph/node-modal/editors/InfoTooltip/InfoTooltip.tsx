import type { TooltipProps } from "@mui/material";
import type { ReactElement, ReactNode } from "react";
import React from "react";

import { InfoTooltipClick } from "./InfoTooltipClick";
import { InfoTooltipHover } from "./InfoTooltipHover";
import { StyledInfo } from "./StyledInfo";
import StyledInfoMarkdown from "./StyledInfoMarkdown";

export interface InfoTooltipProps {
    title: ReactNode | undefined;
    variant?: "hover" | "click";
    children?: ReactElement;
    className?: string;
    customComponentsProps?: TooltipProps["componentsProps"];
    enterDelay?: number;
    placement?: TooltipProps["placement"];
}

export const InfoTooltip = ({
    title,
    variant = "click",
    children = <StyledInfo />,
    className,
    customComponentsProps,
    enterDelay,
    placement,
}: InfoTooltipProps) => {
    if (!title) {
        return children;
    }

    const formattedTitle = typeof title === "string" ? <StyledInfoMarkdown>{title}</StyledInfoMarkdown> : title;

    return variant === "hover" ? (
        <InfoTooltipHover
            title={formattedTitle}
            className={className}
            customComponentsProps={customComponentsProps}
            enterDelay={enterDelay}
            placement={placement}
        >
            {children}
        </InfoTooltipHover>
    ) : (
        <InfoTooltipClick
            title={formattedTitle}
            className={className}
            customComponentsProps={customComponentsProps}
            enterDelay={enterDelay}
            placement={placement}
        >
            {children}
        </InfoTooltipClick>
    );
};
