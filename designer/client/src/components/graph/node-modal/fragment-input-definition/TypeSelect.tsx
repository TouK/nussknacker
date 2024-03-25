import React, { useCallback, useState } from "react";
import Select from "react-select";
import { NodeValue } from "../node";
import { selectStyled } from "../../../../stylesheets/SelectStyled";
import { useTheme } from "@mui/material";
import ValidationLabels from "../../../modals/ValidationLabels";
import { FieldError } from "../editors/Validators";

export interface Option {
    value: string;
    label: string;
}

interface RowSelectProps {
    onChange: (value: string) => void;
    options: Option[];
    readOnly?: boolean;
    isMarked?: boolean;
    value: Option;
    placeholder?: string;
    fieldErrors: FieldError[];
}

export function TypeSelect({ isMarked, options, readOnly, value, onChange, placeholder, fieldErrors }: RowSelectProps): JSX.Element {
    const theme = useTheme();

    const { control, input, valueContainer, singleValue, menuPortal, menu, menuList, menuOption } = selectStyled(theme);

    return (
        <NodeValue className="field" marked={isMarked}>
            <Select
                aria-label={"type-select"}
                className="node-value node-value-select node-value-type-select"
                isDisabled={readOnly}
                maxMenuHeight={190}
                options={options}
                value={value || ""}
                onChange={(option) => onChange(typeof option === "string" ? "" : option.value)}
                menuPortalTarget={document.body}
                placeholder={placeholder}
                styles={{
                    input: (base) => ({ ...input(base) }),
                    control: (base, props) => ({
                        ...control(base, props.isFocused, props.isDisabled),
                    }),
                    menu: (base) => ({
                        ...menu(base),
                    }),
                    menuPortal: (base) => ({
                        ...menuPortal(base),
                    }),
                    menuList: (base) => ({
                        ...menuList(base),
                    }),
                    option: (base, props) => ({
                        ...menuOption(base, props.isSelected, props.isFocused),
                    }),
                    valueContainer: (base, props) => ({
                        ...valueContainer(base, props.hasValue),
                    }),
                    singleValue: (base) => ({ ...singleValue(base) }),
                }}
            />
            <ValidationLabels fieldErrors={fieldErrors} />
        </NodeValue>
    );
}
