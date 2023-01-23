import {cx} from "@emotion/css"
import {defaultsDeep} from "lodash"
import React from "react"
import Select, {Props as SelectProps} from "react-select"
import {useNkTheme} from "../../containers/theme"

const className = "form-select"

export function ThemedSelect<T = string, IsMulti extends boolean = false>(props: SelectProps<T, IsMulti>) {
  const {theme} = useNkTheme()
  return (
    <Select
      className={cx(className)}
      classNamePrefix={className}
      theme={provided => defaultsDeep(theme, provided)}
      styles={{
        clearIndicator: provided => ({
          ...provided,
          padding: theme.spacing.baseUnit,
        }),
        dropdownIndicator: provided => ({
          ...provided,
          padding: theme.spacing.baseUnit,
        }),
        control: (provided, {isFocused, isDisabled}) => ({
          ...provided,
          fontSize: theme.fontSize,
          minHeight: theme.spacing.controlHeight,
          boxShadow: isFocused ? `0 0 0 1px ${theme?.colors?.focusColor}` : null,
          opacity: isDisabled ? theme.colors.primaryBackground : theme.colors.neutral0,
          "&, &:hover": {
            borderColor: theme.colors.neutral30,
          },
        }),
        input: (provided) => ({
          ...provided,
          color: theme.colors.neutral90,
        }),
        option: (provided, {theme, isDisabled, isSelected, isFocused}) => ({
          ...provided,
          fontSize: theme["fontSize"],
          color: isDisabled ?
            theme.colors.neutral20 :
            isSelected || isFocused ?
              theme.colors.primary75 :
              theme.colors.neutral90,
        }),
      }}
      {...props}
    />
  )
}
