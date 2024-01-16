import React from "react";
import { FormLabel } from "@mui/material";

export function NodeLabel({ label, className }: { label: string; className?: string }): JSX.Element {
    return (
        <FormLabel className={className} title={label}>
            {label}:
        </FormLabel>
    );
}
