import { styled, useTheme } from "@mui/material";
import { isEmpty } from "lodash";
import type { HTMLProps } from "react";
import React, { useCallback, useState } from "react";
import CreatableSelect from "react-select/creatable";

import { selectStyled } from "../../../../stylesheets/SelectStyled";
import ValidationLabels from "../../../modals/ValidationLabels";
import type { FieldError } from "../editors/Validators";
import { NodeValue } from "../node/NodeValue";

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
    isUsed?: boolean;
}

interface TypeSelectProps extends Omit<HTMLProps<HTMLSelectElement>, "value" | "options" | "onBlur" | "onChange"> {
    onChange: (value: string) => void;
    onBlur?: (value: string) => void;
    options: Option[];
    readOnly?: boolean;
    isMarked?: boolean;
    value: Option;
    placeholder?: string;
    fieldErrors?: FieldError[];
    menuIsOpen?: boolean;
}

function Select({
    isMarked,
    options,
    readOnly,
    value,
    onChange,
    onBlur,
    placeholder,
    fieldErrors = [],
    menuIsOpen = false,
    ...props
}: TypeSelectProps): React.JSX.Element {
    const { setCaptureEsc, preventEsc } = useCaptureEsc();
    const theme = useTheme();

    const { control, input, valueContainer, singleValue, menuPortal, menu, menuList, menuOption, dropdownIndicator, indicatorSeparator } =
        selectStyled(theme);

    return (
        <NodeValue marked={isMarked} onKeyDown={preventEsc} className={props.className}>
            <CreatableSelect
                menuIsOpen={menuIsOpen || undefined} // We need to pass undefined in case of false, to not handle menu state in a controlled manner
                id={props.id}
                aria-label={"type-select"}
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
                    option: (base, props) => {
                        const data = props.data as Option;
                        return {
                            ...menuOption(base, props.isSelected, props.isDisabled, data.isUsed),
                        };
                    },
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

export const TypeSelect = styled(Select)({});
