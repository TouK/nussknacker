import cn from "classnames"
import {defaultsDeep} from "lodash"
import React, {useCallback} from "react"
import Select, {Props as SelectProps} from "react-select"
import styles from "../../containers/processesTable.styl"
import {useNkTheme} from "../../containers/theme"
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

const className = "form-select"

function StyledSelect<T>(props: SelectProps<T>) {
  const {theme} = useNkTheme()
  return (
    <Select
      className={cn(className)}
      classNamePrefix={className}
      theme={provided => defaultsDeep(theme, provided)}
      styles={{
        control: (provided) => ({
          ...provided,
          fontSize: theme.fontSize,
        }),
        input: (provided, {theme}) => ({
          ...provided,
          color: theme.colors.neutral90,
        }),
        option: (provided, {theme, isDisabled, isSelected, isFocused}) => ({
          ...provided,
          fontSize: theme.fontSize,
          color: isDisabled ?
            theme.colors.neutral20 :
            isSelected || isFocused ?
              theme.colors.neutral0 :
              theme.colors.neutral90,
        }),
      }}
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
