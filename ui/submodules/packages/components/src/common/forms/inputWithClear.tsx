import { Clear } from "@mui/icons-material";
import { IconButton, InputAdornment, OutlinedInput, OutlinedInputProps } from "@mui/material";
import React from "react";

type InputWithClearProps = OutlinedInputProps & {
    value: string;
    onChange: (value: string) => void;
};

export function InputWithClear({ value, onChange, ...props }: InputWithClearProps): JSX.Element {
    return (
        <OutlinedInput
            {...props}
            value={value}
            onChange={(e) => onChange(e.target.value)}
            endAdornment={
                value && (
                    <InputAdornment position="end">
                        <IconButton
                            aria-label="clear"
                            onClick={() => onChange(null)}
                            onMouseDown={(event) => event.preventDefault()}
                            edge="end"
                        >
                            <Clear />
                        </IconButton>
                    </InputAdornment>
                )
            }
        />
    );
}
