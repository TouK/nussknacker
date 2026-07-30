import { cx } from "@emotion/css";
import { useTheme } from "@mui/material";
import { isEmpty } from "lodash";
import type { HTMLProps } from "react";
import React, { useCallback, useState } from "react";
import CreatableSelect from "react-select/creatable";

import { selectStyled } from "../../../../stylesheets/SelectStyled";
import ValidationLabels from "../../../modals/ValidationLabels";
import type { FieldError } from "../editors/Validators";
import { NodeValue } from "../node";
import { nodeValue } from "../NodeDetailsContent/NodeTableStyled";

function useCaptureEsc() {
    const [captureEsc, setCaptureEsc] = useState(false);

    //prevent modal close by esc
    const preventEsc = useCallback(
        (event: React.KeyboardEvent) => {
            if (captureEsc && event.key === "Escape") {
                event.stopPropagation();
            }
        },
        [captureEsc],
    );

    return { setCaptureEsc, preventEsc };
}

export interface Option {
    value: string;
    label: string;
    isDisabled?: boolean;
    description?: string;
    comment?: string;
}

interface RowSelectProps extends Omit<HTMLProps<HTMLSelectElement>, "value" | "options" | "onBlur" | "onChange"> {
    onChange: (value: string) => void;
    onBlur?: (value: string) => void;
    options: Option[];
    readOnly?: boolean;
    isMarked?: boolean;
    value: Option;
    placeholder?: string;
    fieldErrors?: FieldError[];
    selectComponents?: React.ComponentProps<typeof CreatableSelect<Option>>["components"];
    isLoading?: boolean;
    noOptionsMessage?: React.ComponentProps<typeof CreatableSelect<Option>>["noOptionsMessage"];
    isValidNewOption?: React.ComponentProps<typeof CreatableSelect<Option>>["isValidNewOption"];
}

export function TypeSelect({
    isMarked,
    options,
    readOnly,
    value,
    onChange,
    onBlur,
    placeholder,
    fieldErrors = [],
    selectComponents,
    isLoading,
    noOptionsMessage,
    isValidNewOption,
    ...props
}: RowSelectProps): JSX.Element {
    const { setCaptureEsc, preventEsc } = useCaptureEsc();
    const theme = useTheme();

    const { control, input, valueContainer, singleValue, menuPortal, menu, menuList, menuOption, dropdownIndicator, indicatorSeparator } =
        selectStyled(theme);

    return (
        <NodeValue marked={isMarked} onKeyDown={preventEsc} sx={{ width: "100%" }}>
            <CreatableSelect<Option>
                id={props.id}
                aria-label={"type-select"}
                className={cx(`${nodeValue}`, props.className)}
                isDisabled={readOnly}
                maxMenuHeight={190}
                onMenuOpen={() => setCaptureEsc(true)}
                onMenuClose={() => setCaptureEsc(false)}
                components={selectComponents}
                isLoading={isLoading}
                noOptionsMessage={noOptionsMessage}
                isValidNewOption={isValidNewOption}
                options={options}
                value={value || null}
                onChange={(option) => onChange(typeof option === "string" ? "" : option.value)}
                onBlur={(e) => onBlur?.(e.target.value)}
                menuPortalTarget={document.body}
                placeholder={placeholder}
                styles={{
                    input: (base) => ({ ...input(base) }),
                    control: (base, props) => ({
                        ...control(base, props.isFocused, props.isDisabled, !isEmpty(fieldErrors)),
                    }),
                    dropdownIndicator: (base) => ({
                        ...dropdownIndicator(base),
                    }),
                    indicatorSeparator: (base) => ({
                        ...indicatorSeparator(base),
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
                        ...menuOption(base, props.isSelected, props.isDisabled),
                    }),
                    valueContainer: (base, props) => ({
                        ...valueContainer(base),
                    }),
                    singleValue: (base, props) => ({ ...singleValue(base, props.isDisabled) }),
                }}
            />
            <ValidationLabels fieldErrors={fieldErrors} />
        </NodeValue>
    );
}
