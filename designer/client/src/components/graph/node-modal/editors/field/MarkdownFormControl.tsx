import { FormControl } from "@mui/material";
import type { PropsWithChildren } from "react";
import React from "react";

import { CustomCompleterAceEditor } from "../expression/CustomCompleterAceEditor";
import { ExpressionLang } from "../expression/types";
import type { FieldProps } from "./Field";

type MarkdownFormControlProps = Omit<FieldProps, "type"> &
    PropsWithChildren<{
        value: string;
        onChange: (value: string) => void;
    }>;

export const MarkdownFormControl = ({ value, onChange, className, children, readOnly, ...props }: MarkdownFormControlProps) => (
    <FormControl>
        {children}
        <CustomCompleterAceEditor
            {...props}
            enableLiveAutocompletion={false}
            inputProps={{
                language: ExpressionLang.MD,
                className,
                value,
                onValueChange: onChange,
                readOnly,
            }}
        />
    </FormControl>
);
