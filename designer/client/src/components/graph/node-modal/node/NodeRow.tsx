import type { HTMLAttributes } from "react";
import React, { forwardRef } from "react";

import { FormControl, FormLabel } from "../editors/FormControl";

interface Props extends Omit<HTMLAttributes<HTMLDivElement>, "color"> {
    label?: string;
    title?: string;
    required?: boolean;
}

export const NodeRow = forwardRef<HTMLDivElement, Props>(function FieldRow(props, ref): React.JSX.Element {
    const { label, className, children, required, title, ...passProps } = props;
    return (
        <FormControl ref={ref} className={className} {...passProps}>
            <>
                {label && (
                    <FormLabel required={required} title={title || label}>
                        {label}:
                    </FormLabel>
                )}
                {children}
            </>
        </FormControl>
    );
});
