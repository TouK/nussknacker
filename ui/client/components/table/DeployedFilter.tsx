import TableSelect from "./TableSelect"
import React from "react"
import {Option, FilterProps} from "./FilterTypes"
import {useParseValue} from "./useParseValue"

const options: Option<boolean>[] = [
  {label: "Show all processes"},
  {label: "Show only deployed processes", value: true},
  {label: "Show only not deployed processes", value: false},
]

export function DeployedFilter(props: FilterProps<boolean>) {
  const {onChange} = props
  const value = useParseValue(options, props.value)
  return (
    <TableSelect
      defaultValue={value}
      options={options}
      placeholder="Select deployed info..."
      onChange={({value}) => onChange(value)}
      isMulti={false}
      isSearchable={false}
    />
  )
}
