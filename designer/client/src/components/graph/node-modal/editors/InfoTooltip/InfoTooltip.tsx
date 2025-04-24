import React from "react";

import { InfoTooltipClick } from "./InfoTooltipClick";
import { InfoTooltipHover } from "./InfoTooltipHover";

interface Props {
    text: string;
    variant?: "hover" | "click";
}

export const InfoTooltip = ({ text, variant = "click" }: Props) =>
    variant === "hover" ? <InfoTooltipHover text={text} /> : <InfoTooltipClick text={text} />;
