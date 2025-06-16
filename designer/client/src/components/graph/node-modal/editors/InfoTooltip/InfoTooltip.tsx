import type { TooltipProps } from "@mui/material";
import type { ReactElement } from "react";
import React from "react";

import { InfoTooltipClick } from "./InfoTooltipClick";
import { InfoTooltipHover } from "./InfoTooltipHover";
import { StyledInfo } from "./StyledInfo";

export interface Props {
    title: string | undefined;
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
}: Props) => {
    if (!title) {
        return children;
    }

    return variant === "hover" ? (
        <InfoTooltipHover title={title} className={className} customComponentsProps={customComponentsProps} enterDelay={enterDelay}>
            {children}
        </InfoTooltipHover>
    ) : (
        <InfoTooltipClick title={title} className={className} customComponentsProps={customComponentsProps} enterDelay={enterDelay}>
            {children}
        </InfoTooltipClick>
    );
};
