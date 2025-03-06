import { FormControl, FormLabel } from "@mui/material";
import React from "react";
import { Option, TypeSelect } from "../fragment-input-definition/TypeSelect";

interface TestCaseSelectProps {
    label: string;
    value: string;
    onChange: (value: string) => void;
    direction?: "input" | "output";
    availableContexts: Option[];
}

export function TestCaseSelect(props: TestCaseSelectProps): JSX.Element {
    const { label, value, onChange, availableContexts } = props;
    return (
        <FormControl>
            <FormLabel>{label}</FormLabel>
            <TypeSelect
                onChange={onChange}
                options={availableContexts}
                value={availableContexts.find((availableContext) => availableContext.value === value)}
            />
        </FormControl>
    );
}
