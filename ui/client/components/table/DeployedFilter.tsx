import React from "react"
import {ValueFieldProps} from "../valueField"
import TableSelect, {OptionType} from "./TableSelect"
import {useParseValue} from "./useParseValue"

const options: OptionType<boolean>[] = [
  {label: "Show all processes"},
  {label: "Show only deployed processes", value: true},
  {label: "Show only not deployed processes", value: false},
]

export function DeployedFilter(props: ValueFieldProps<boolean>) {
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
