import {Option, FilterProps} from "./FilterTypes"
import TableSelect from "./TableSelect"
import React from "react"
import {useParseValue} from "./useParseValue"

const options: Option<boolean>[] = [
  {label: "Show all types processes"},
  {label: "Show only processes", value: false},
  {label: "Show only subprocesses", value: true},
]

export function SubprocessFilter(props: FilterProps<boolean>) {
  const {onChange} = props
  const value = useParseValue(options, props.value)
  return (
    <TableSelect
      defaultValue={value}
      options={options}
      placeholder="Select process type.."
      onChange={({value}) => onChange(value)}
      isMulti={false}
      isSearchable={false}
    />
  )
}
