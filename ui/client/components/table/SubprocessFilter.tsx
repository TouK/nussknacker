import React from "react"
import {ValueFieldProps} from "../valueField"
import TableSelect, {OptionType} from "./TableSelect"
import {useParseValue} from "./useParseValue"

const options: OptionType<boolean>[] = [
  {label: "Show all types scenarios"},
  {label: "Show only scenarios", value: false},
  {label: "Show only fragments", value: true},
]

export function SubprocessFilter(props: ValueFieldProps<boolean>): JSX.Element {
  const {onChange} = props
  const value = useParseValue(options, props.value)
  return (
    <TableSelect
      value={value}
      options={options}
      placeholder="Select process type.."
      onChange={({value}) => onChange(value)}
      isMulti={false}
      isSearchable={false}
    />
  )
}
