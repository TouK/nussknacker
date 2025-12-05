import { cx } from "@emotion/css";
import { Stack, useTheme } from "@mui/material";
import { isEmpty } from "lodash";
import React from "react";
import Creatable from "react-select/creatable";

import { selectStyled } from "../../../../../stylesheets/SelectStyled";
import ValidationLabels from "../../../../modals/ValidationLabels";
import type { FieldError } from "../Validators";
import type { OnValueChange } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import type { Option } from "./FixedValuesEditor";
import { NodeIcon, StyledOptionLabel, truncateOptionLabel } from "./FixedValuesEditor";
import { EditorType } from "./types";

export function ReactSelectCreatableVariant({
    className,
    currentOption,
    fieldErrors,
    onValueChange,
    options,
    readOnly,
    showValidation,
}: {
    className?: string;
    currentOption: Option;
    fieldErrors: FieldError[];
    onValueChange: OnValueChange;
    options: Option[];
    readOnly?: boolean;
    showValidation?: boolean;
}) {
    const theme = useTheme();

    const { control, input, valueContainer, singleValue, menuPortal, menu, menuList, menuOption, indicatorSeparator, dropdownIndicator } =
        selectStyled(theme);

    return (
        <div className={cx(className)}>
            <Creatable
                value={currentOption}
                classNamePrefix={"test"}
                onChange={(newValue) =>
                    onValueChange({
                        expression: newValue.value,
                        language: editorsParameters[EditorType.FIXED_VALUES_PARAMETER_EDITOR].language,
                    })
                }
                options={options}
                formatOptionLabel={(option) =>
                    option.icon ? (
                        <Stack direction={"row"} alignItems={"center"} spacing={1}>
                            <NodeIcon src={option.icon} />
                            <StyledOptionLabel role="option">{truncateOptionLabel(option.label)}</StyledOptionLabel>
                        </Stack>
                    ) : (
                        <StyledOptionLabel role="option">{truncateOptionLabel(option.label)}</StyledOptionLabel>
                    )
                }
                isDisabled={readOnly}
                formatCreateLabel={(x) => x}
                menuPortalTarget={document.body}
                createOptionPosition={"first"}
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
                    valueContainer: (base) => ({
                        ...valueContainer(base),
                    }),
                    singleValue: (base) => ({ ...singleValue(base, readOnly) }),
                }}
            />

            {showValidation && <ValidationLabels fieldErrors={fieldErrors} />}
        </div>
    );
}
