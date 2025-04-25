import type { ReactElement } from "react";
import React from "react";

import { InfoTooltipClick } from "./InfoTooltipClick";
import { InfoTooltipHover } from "./InfoTooltipHover";
import { StyledInfo } from "./StyledInfo";

interface Props {
    text: string | undefined;
    variant?: "hover" | "click";
    children?: ReactElement;
    className?: string;
}

export const InfoTooltip = ({ text, variant = "click", children = <StyledInfo />, className }: Props) => {
    if (!text) {
        return children;
    }

    return variant === "hover" ? (
        <InfoTooltipHover text={text} className={className}>
            {children}
        </InfoTooltipHover>
    ) : (
        <InfoTooltipClick text={text} className={className}>
            {children}
        </InfoTooltipClick>
    );
};
