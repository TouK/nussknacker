import { Autocomplete, FormControl } from "@mui/material";
import { nodeInput } from "../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import React from "react";

export const SearchLabeledAutocomplete = ({ children, name, values }) => {
    return (
        <FormControl sx={{ display: "flex", flexDirection: "column", m: 0, gap: 1, width: "100%" }}>
            {children}
            <Autocomplete
                options={values}
                className={nodeInput}
                renderInput={(params) => (
                    <div ref={params.InputProps.ref}>
                        <input name={name} {...params.inputProps} placeholder="Type to search..." className={nodeInput} />
                    </div>
                )}
            />
        </FormControl>
    );
};

SearchLabeledAutocomplete.displayName = "SearchLabeledAutocomplete";
