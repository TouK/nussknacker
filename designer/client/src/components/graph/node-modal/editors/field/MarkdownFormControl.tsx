import { FormControl } from "@mui/material";
import { CustomCompleterAceEditor } from "../expression/CustomCompleterAceEditor";
import { ExpressionLang } from "../expression/types";
import React, { PropsWithChildren } from "react";
import { FieldProps } from "./Field";

type MarkdownFormControlProps = Omit<FieldProps, "type"> &
    PropsWithChildren<{
        value: string;
        onChange: (value: string) => void;
    }>;

export const MarkdownFormControl = ({ value, onChange, className, children, ...props }: MarkdownFormControlProps) => (
    <FormControl>
        {children}
        <CustomCompleterAceEditor
            {...props}
            inputProps={{
                language: ExpressionLang.MD,
                className,
                value,
                onValueChange: onChange,
            }}
        />
    </FormControl>
);
