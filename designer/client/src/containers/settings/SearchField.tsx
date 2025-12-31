import { Clear, Search } from "@mui/icons-material";
import { IconButton, InputAdornment, TextField } from "@mui/material";
import { isEmpty } from "lodash";
import React, { useRef } from "react";
import { useDocumentEventListener } from "rooks";

function isSimpleChar(event: KeyboardEvent) {
    const hasModifiers = event.shiftKey || event.altKey || event.ctrlKey || event.metaKey;
    const simpleCharacters = event.key.length === 1 && /^[a-zA-Z0-9]$/.test(event.key);
    return simpleCharacters && !hasModifiers;
}

function isOutsideInput() {
    const activeElement = document.activeElement as HTMLElement;
    const tagName = activeElement.tagName.toLowerCase();
    const inputTagNames = ["input", "textarea", "select"];
    return !inputTagNames.includes(tagName);
}

export function SearchField({ value, onChange }: { value: string; onChange: (value: string) => void }) {
    const ref = useRef<HTMLInputElement>(null);

    useDocumentEventListener("keydown", (event: KeyboardEvent) => {
        if (isSimpleChar(event) && isOutsideInput()) {
            ref.current?.select();
            ref.current?.focus();
        }
    });

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
