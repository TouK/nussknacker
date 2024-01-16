import React, { forwardRef } from "react";
import { FormControl, FormLabel } from "@mui/material";

interface Props extends React.DetailedHTMLProps<React.HTMLAttributes<HTMLDivElement>, HTMLDivElement> {
    label?: string;
}

export const NodeRow = forwardRef<HTMLDivElement, Props>(function FieldRow(props, ref): JSX.Element {
    const { label, className, children } = props;
    return (
        <FormControl ref={ref} className={className}>
            <>
                {label && <FormLabel title={label}>{label}</FormLabel>}
                {children}
            </>
        </FormControl>
    );
});
