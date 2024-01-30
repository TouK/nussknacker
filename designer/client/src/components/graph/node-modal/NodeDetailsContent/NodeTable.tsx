import React, { PropsWithChildren } from "react";
import { cx } from "@emotion/css";
import { NodeTableStyled } from "./NodeTableStyled";

export function NodeTable({ children, className, editable }: PropsWithChildren<{ className?: string; editable?: boolean }>): JSX.Element {
    return <NodeTableStyled className={cx({ "node-editable": editable }, className)}>{children}</NodeTableStyled>;
}
