import React from "react";
import { NodeLabelStyled } from "./NodeStyled";

export function NodeLabel({ label, className }: { label: string; className?: string }): JSX.Element {
    return (
        <NodeLabelStyled className={className} title={label}>
            {label}:
        </NodeLabelStyled>
    );
}
