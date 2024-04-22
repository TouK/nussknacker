import { cx } from "@emotion/css";
import React from "react";
import { nodeValue } from "../NodeDetailsContent/NodeTableStyled";

interface Props extends React.DetailedHTMLProps<React.HTMLAttributes<HTMLDivElement>, HTMLDivElement> {
    marked?: boolean; //mark change in diff
}

export function NodeValue({ children, className, marked, ...props }: Props): JSX.Element {
    return (
        <div className={cx(nodeValue, className, { marked })} {...props}>
            {children}
        </div>
    );
}
