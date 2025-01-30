import { Autocomplete, FormControl } from "@mui/material";
import { nodeInput } from "../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import React from "react";

export const SearchLabeledAutocomplete = ({ children, name, options, value, setFilterFields }) => {
    function handleChange(_, value) {
        setFilterFields((prev) => ({ ...prev, [name]: [value] }));
    }

    return (
        <FormControl sx={{ display: "flex", flexDirection: "column", m: 0, gap: 1, width: "100%" }} fullWidth={true}>
            {children}
            <Autocomplete
                freeSolo
                options={options}
                value={value.join(",")}
                onChange={handleChange}
                onInputChange={handleChange}
                renderInput={(params) => (
                    <div ref={params.InputProps.ref}>
                        <input name={name} {...params.inputProps} className={nodeInput} style={{ width: "100%" }} />
                    </div>
                )}
            />
        </FormControl>
    );
};

SearchLabeledAutocomplete.displayName = "SearchLabeledAutocomplete";
