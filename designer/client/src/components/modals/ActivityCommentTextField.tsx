import { styled, TextField, TextFieldProps } from "@mui/material";
import React from "react";

export const ActivityCommentTextField = styled((props: TextFieldProps) => (
    <TextField
        fullWidth
        multiline
        minRows={1}
        maxRows={3}
        InputLabelProps={{ shrink: true }}
        variant="outlined"
        label="Comment"
        {...props}
    />
))({
    flexDirection: "column",
    ".MuiFormLabel-root": {
        margin: 0,
        flexDirection: "column",
    },
});
