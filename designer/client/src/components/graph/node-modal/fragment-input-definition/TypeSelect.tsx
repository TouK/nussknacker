import React, { useCallback, useState } from "react";
import Select from "react-select";
import { NodeValue } from "./NodeValue";
import { selectStyled } from "../../../../stylesheets/SelectStyled";
import { useTheme } from "@mui/material";

export interface Option {
    value: string;
    label: string;
}

interface RowSelectProps {
    onChange: (value: string) => void;
    options: Option[];
    readOnly?: boolean;
    isMarked?: boolean;
    value?: Option;
}

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

export function TypeSelect({ isMarked, options, readOnly, value, onChange }: RowSelectProps): JSX.Element {
    const { setCaptureEsc, preventEsc } = useCaptureEsc();
    const theme = useTheme();

    const { control, input, valueContainer, singleValue, menuPortal, menu, menuList, menuOption } = selectStyled(theme);

    return (
        <NodeValue className="field" marked={isMarked} onKeyDown={preventEsc}>
            <Select
                aria-label={"type-select"}
                className="node-value node-value-select node-value-type-select"
                isDisabled={readOnly}
                maxMenuHeight={190}
                onMenuOpen={() => setCaptureEsc(true)}
                onMenuClose={() => setCaptureEsc(false)}
                options={options}
                value={value}
                onChange={(option) => onChange(option.value)}
                menuPortalTarget={document.body}
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
                    singleValue: (base, props) => ({ ...singleValue(base, props.isDisabled) }),
                }}
            />
        </NodeValue>
    );
}
