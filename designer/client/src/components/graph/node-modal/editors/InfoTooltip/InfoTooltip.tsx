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
}

export const InfoTooltip = ({
    title,
    variant = "click",
    children = <StyledInfo />,
    className,
    customComponentsProps,
    enterDelay,
}: InfoTooltipProps) => {
    if (!title) {
        return children;
    }

    return variant === "hover" ? (
        <InfoTooltipHover
            title={typeof title === "string" ? <StyledInfoMarkdown>{title}</StyledInfoMarkdown> : title}
            className={className}
            customComponentsProps={customComponentsProps}
            enterDelay={enterDelay}
        >
            {children}
        </InfoTooltipHover>
    ) : (
        <InfoTooltipClick
            title={typeof title === "string" ? <StyledInfoMarkdown>{title}</StyledInfoMarkdown> : title}
            className={className}
            customComponentsProps={customComponentsProps}
            enterDelay={enterDelay}
        >
            {children}
        </InfoTooltipClick>
    );
};
