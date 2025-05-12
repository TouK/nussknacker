import { FormControl, FormLabel } from "@mui/material";
import type { HTMLAttributes } from "react";
import React, { forwardRef } from "react";

interface Props extends Omit<HTMLAttributes<HTMLDivElement>, "color"> {
    label?: string;
}

export const NodeRow = forwardRef<HTMLDivElement, Props>(function FieldRow(props, ref): JSX.Element {
    const { label, className, children, ...passProps } = props;
    return (
        <FormControl ref={ref} className={className} {...passProps}>
            <>
                {label && <FormLabel title={label}>{label}:</FormLabel>}
                {children}
            </>
        </FormControl>
    );
});
