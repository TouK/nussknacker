import React, { useCallback, useState } from "react";
import Select from "react-select";
import { NodeValue } from "../node";
import { selectStyled } from "../../../../stylesheets/SelectStyled";
import { useTheme } from "@mui/material";
import ValidationLabels from "../../../modals/ValidationLabels";
import { FieldError } from "../editors/Validators";
import { cx } from "@emotion/css";

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
}

interface RowSelectProps {
    id?: string;
    className?: string;
    onChange: (value: string) => void;
    onBlur?: (value: string) => void;
    options: Option[];
    readOnly?: boolean;
    isMarked?: boolean;
    value: Option;
    placeholder?: string;
    fieldErrors: FieldError[];
}

export function TypeSelect({
    id,
    className,
    isMarked,
    options,
    readOnly,
    value,
    onChange,
    onBlur,
    placeholder,
    fieldErrors,
}: RowSelectProps): JSX.Element {
    const { setCaptureEsc, preventEsc } = useCaptureEsc();
    const theme = useTheme();

    const { control, input, valueContainer, singleValue, menuPortal, menu, menuList, menuOption } = selectStyled(theme);

    return (
        <NodeValue className="field" marked={isMarked} onKeyDown={preventEsc}>
            <Select
                id={id}
                aria-label={"type-select"}
                className={cx("node-value node-value-select node-value-type-select", className)}
                isDisabled={readOnly}
                maxMenuHeight={190}
                onMenuOpen={() => setCaptureEsc(true)}
                onMenuClose={() => setCaptureEsc(false)}
                options={options}
                value={value || ""}
                onChange={(option) => onChange(typeof option === "string" ? "" : option.value)}
                onBlur={(e) => onBlur(e.target.value)}
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
