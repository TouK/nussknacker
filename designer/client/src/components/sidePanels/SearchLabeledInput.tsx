import { FormControl } from "@mui/material";
import React from "react";

import { nodeInput } from "../graph/node-modal/NodeDetailsContent/NodeTableStyled";

export const SearchLabeledInput = ({ children, name, value, setFilterFields }) => {
    function handleChange(event) {
        setFilterFields((prev) => ({ ...prev, [name]: event.target.value.split(",") }));
    }

    return (
        <FormControl sx={{ display: "flex", flexDirection: "column", m: 0, gap: 1, width: "100%" }}>
            {children}
            <input name={name} value={value} className={nodeInput} onChange={handleChange} />
        </FormControl>
    );
};

SearchLabeledInput.displayName = "SearchLabeledInput";
