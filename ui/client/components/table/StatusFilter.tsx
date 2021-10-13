import React from "react"
import {ValueFieldProps} from "../valueField"
import TableSelect, {OptionType} from "./TableSelect"
import {useParseValue} from "./useParseValue"

const options: OptionType<boolean | never>[] = [
  {label: "Show all scenarios"},
  {label: "Show only deployed scenarios", value: true},
  {label: "Show only not deployed scenarios", value: false},
]

export function StatusFilter(props: ValueFieldProps<boolean | never>): JSX.Element {
  const {onChange} = props
  const value = useParseValue(options, props.value)
  return (
    <TableSelect
      value={value}
      options={options}
      placeholder="Select deployed info..."
      onChange={({value}) => onChange(value)}
      isMulti={false}
      isSearchable={false}
    />
  )
}
