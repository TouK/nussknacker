import ClearIcon from "@mui/icons-material/Clear";
import { IconButton, InputAdornment, TextField, TextFieldProps } from "@mui/material";
import React from "react";
import { Except } from "type-fest";

type Props = {
    value: string;
    onChange: (value: string) => void;
} & Except<TextFieldProps, "value" | "onChange">;

export function TextFieldWithClear({ value, onChange, ...props }: Props): JSX.Element {
    return (
        <TextField
            {...props}
            value={value}
            onChange={(e) => onChange(e.target.value)}
            InputProps={{
                ...props.InputProps,
                endAdornment: value && (
                    <InputAdornment position="end">
                        <IconButton
                            aria-label="clear"
                            onClick={() => onChange(null)}
                            onMouseDown={(event) => event.preventDefault()}
                            edge="end"
                        >
                            <ClearIcon />
                        </IconButton>
                    </InputAdornment>
                ),
            }}
        />
    );
}
