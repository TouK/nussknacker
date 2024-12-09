import React, { PropsWithChildren } from "react";
import { FormControl } from "@mui/material";
import { nodeInput } from "../graph/node-modal/NodeDetailsContent/NodeTableStyled";

export const SearchLabeledInput = ({ children, ...props }) => {
    return (
        <FormControl sx={{ display: "flex", flexDirection: "column", m: 0, gap: 1, width: "100%" }}>
            {children}
            <input {...props} className={nodeInput} />
        </FormControl>
    );
};

SearchLabeledInput.displayName = "SearchLabeledInput";
