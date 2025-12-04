import { cx } from "@emotion/css";
import { FormControlLabel, Radio, RadioGroup } from "@mui/material";
import React from "react";

import type { OnValueChange } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import type { FixedValuesEditorProps, Option } from "./FixedValuesEditor";
import { EditorType } from "./types";

export function RadioVariant({
    className,
    currentOption,
    onValueChange,
    options,
    param,
}: {
    className?: string;
    currentOption: Option;
    onValueChange: OnValueChange;
    options: Option[];
    param?: FixedValuesEditorProps["param"];
}) {
    return (
        <div className={cx(className)}>
            <RadioGroup
                value={currentOption.value}
                onChange={(event) =>
                    onValueChange({
                        expression: event.target.value,
                        language: editorsParameters[EditorType.FIXED_VALUES_WITH_RADIO_PARAMETER_EDITOR].language,
                    })
                }
            >
                {options.map((option: Option) => {
                    const label = option.value === param?.defaultValue ? `${option.label} (default)` : option.label;
                    return <FormControlLabel key={option.value} value={option.value} control={<Radio />} label={label} />;
                })}
            </RadioGroup>
        </div>
    );
}
