import React, { forwardRef } from "react";
import { NodeLabel } from "./NodeLabel";
import { NodeRow as NodeRowStyled } from "../../node-modal/NodeDetailsContent/NodeStyled";

interface Props extends React.DetailedHTMLProps<React.HTMLAttributes<HTMLDivElement>, HTMLDivElement> {
    label?: string;
}

export const NodeRow = forwardRef<HTMLDivElement, Props>(function FieldRow(props, ref): JSX.Element {
    const { label, className, children, ...passProps } = props;
    return (
        <NodeRowStyled ref={ref} className={className} {...passProps}>
            <>
                {label && <NodeLabel label={label} />}
                {children}
            </>
        </NodeRowStyled>
    );
});
