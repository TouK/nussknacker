import React, { HTMLProps, useCallback, useState } from "react";
import Select from "react-select";
import { NodeValue } from "../node";
import { selectStyled } from "../../../../stylesheets/SelectStyled";
import { useTheme } from "@mui/material";
import ValidationLabels from "../../../modals/ValidationLabels";
import { FieldError } from "../editors/Validators";
import { cx } from "@emotion/css";
import { nodeValue } from "../NodeDetailsContent/NodeTableStyled";
import { isEmpty } from "lodash";

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
    ...props
}: RowSelectProps): JSX.Element {
    const { setCaptureEsc, preventEsc } = useCaptureEsc();
    const theme = useTheme();

    const { control, input, valueContainer, singleValue, menuPortal, menu, menuList, menuOption, dropdownIndicator, indicatorSeparator } =
        selectStyled(theme);

    return (
        <NodeValue marked={isMarked} onKeyDown={preventEsc} sx={{ width: "100%" }}>
            <Select
                id={props.id}
                aria-label={"type-select"}
                className={cx(`${nodeValue}`, props.className)}
                isDisabled={readOnly}
                maxMenuHeight={190}
                onMenuOpen={() => setCaptureEsc(true)}
                onMenuClose={() => setCaptureEsc(false)}
                options={options}
                value={value || ""}
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
