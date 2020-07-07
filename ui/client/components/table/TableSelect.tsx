import cn from "classnames"
import React, {useCallback} from "react"
import Select, {Props as SelectProps} from "react-select"
import {StylesConfig} from "react-select/src/styles"
import styles from "../../containers/processesTable.styl"
import {defaultAppTheme} from "../../containers/theme"
import "../../stylesheets/processes.styl"

type OptionType = $TodoType
type Props = {
  defaultValue: $TodoType,
  onChange: $TodoType,
  options: OptionType[],
  isMulti: boolean,
  isSearchable: boolean,
  placeholder: string,
}

const {borderRadius, fontSize, inputHeight, colors} = defaultAppTheme

const customSelectStyles: StylesConfig = {
  control: styles => ({
    ...styles,
    fontSize,
    minHeight: inputHeight,
  }),
  option: (styles, {theme, isDisabled, isSelected, isFocused}) => ({
    ...styles,
    fontSize,
    color: isDisabled ?
      theme.colors.neutral20 :
      isSelected || isFocused ?
        theme.colors.neutral0 :
        theme.colors.neutral90,
  }),
}

const className = "form-select"

function StyledSelect<T>(props: SelectProps<T>) {
  return (
    <Select
      className={cn(className)}
      classNamePrefix={className}
      styles={customSelectStyles}
      theme={theme => ({
        ...theme,
        borderRadius,
        colors: {
          ...theme.colors,
          ...colors,
        },
      })}
      {...props}
    />
  )
}

export default function TableSelect(props: Props) {
  const {onChange, isMulti} = props

  const getOnChange = useCallback(value => {
    onChange(isMulti ? value || [] : value)
  }, [onChange, isMulti])

  return (
    <div className={cn("table-filter", "input-group", styles.filterInput)}>
      <StyledSelect
        {...props}
        closeMenuOnSelect={false}
        onChange={getOnChange}
      />
    </div>
  )
}
