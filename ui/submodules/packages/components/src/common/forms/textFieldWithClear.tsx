import ClearIcon from "@mui/icons-material/BackspaceOutlined";
import { IconButton, InputAdornment, TextField, TextFieldProps } from "@mui/material";
import React from "react";

type Props = TextFieldProps & {
    value: string;
    onChange: (value?: string) => void;
};

export function TextFieldWithClear({ value, onChange, ...props }: Props): JSX.Element {
    return (
        <TextField
            {...props}
            value={value}
            onChange={(e) => onChange(e.target.value.toLowerCase())}
            InputProps={{
                ...props.InputProps,
                endAdornment: value && (
                    <InputAdornment position="end">
                        <IconButton
                            aria-label="clear"
                            onClick={() => onChange()}
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
