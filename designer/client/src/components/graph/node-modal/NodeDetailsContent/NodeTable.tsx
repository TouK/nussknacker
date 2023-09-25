import React, { PropsWithChildren } from "react";
import { cx } from "@emotion/css";
import { NodeTableStyled } from "./NodeTableStyled";

export function NodeTable({ children, className, editable }: PropsWithChildren<{ className?: string; editable?: boolean }>): JSX.Element {
    return <NodeTableStyled className={cx({ "node-editable": editable }, className)}>{children}</NodeTableStyled>;
}

export function NodeTableBody({ children, className }: PropsWithChildren<{ className?: string }>): JSX.Element {
    return (
        <div style={{ clear: "both" }} className={className}>
            {children}
        </div>
    );
}
