import { forwardRef, PropsWithChildren } from "react";
import { FormControl } from "@mui/material";
import { nodeInput } from "../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import React from "react";

export type SearchLabeledInputProps = PropsWithChildren<{
    name: string;
}>;

export const SearchLabeledInput = forwardRef<HTMLInputElement, SearchLabeledInputProps>(({ children, ...props }, ref) => {
    return (
        <FormControl sx={{ display: "flex", flexDirection: "column", m: 0, gap: 1 }}>
            {children}
            <input ref={ref} {...props} className={nodeInput} />
        </FormControl>
    );
});

SearchLabeledInput.displayName = "SearchLabeledInput";
