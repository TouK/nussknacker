import { Clear, Search } from "@mui/icons-material";
import { IconButton, InputAdornment, TextField } from "@mui/material";
import { isEmpty } from "lodash";
import React from "react";

export function SearchField({ value, onChange }: { value: string; onChange: (value: string) => void }) {
    return (
        <TextField
            value={value}
            onChange={(event) => onChange(event.target.value)}
            variant="standard"
            InputProps={{
                startAdornment: (
                    <InputAdornment position="start">
                        <Search />
                    </InputAdornment>
                ),
                endAdornment: isEmpty(value) ? null : (
                    <InputAdornment position="end">
                        <IconButton onClick={() => onChange("")} edge="end">
                            <Clear />
                        </IconButton>
                    </InputAdornment>
                ),
            }}
        />
    );
}
