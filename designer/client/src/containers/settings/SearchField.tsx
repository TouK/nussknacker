import { Clear, Search } from "@mui/icons-material";
import { IconButton, InputAdornment, TextField } from "@mui/material";
import { isEmpty } from "lodash";
import React, { useRef } from "react";

export function SearchField({ value, onChange }: { value: string; onChange: (value: string) => void }) {
    const ref = useRef<HTMLInputElement>(null);
    return (
        <TextField
            inputRef={ref}
            value={value}
            onChange={(event) => onChange(event.target.value)}
            variant="outlined"
            onClick={() => {
                ref.current.focus();
            }}
            InputProps={{
                autoComplete: "off",
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
            sx={(theme) => ({
                flex: 1,
                transition: theme.transitions.create("max-width"),
                maxWidth: 160,
                "&:focus-within, &:has(input[value]:not([value='']))": {
                    maxWidth: "50%",
                },
            })}
        />
    );
}
