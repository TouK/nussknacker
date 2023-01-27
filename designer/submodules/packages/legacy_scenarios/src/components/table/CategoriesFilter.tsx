import React from "react"
import {useFilterCategories} from "../../reducers/selectors/settings"
import {ValueFieldProps} from "../valueField"
import TableSelect, {OptionType} from "./TableSelect"
import {useParseValues} from "./useParseValue"

export function CategoriesFilter(props: ValueFieldProps<string[]>): JSX.Element {
  const {onChange} = props
  const options: OptionType<string>[] = useFilterCategories()
  const value = useParseValues<string>(options, props.value)
  return (
    <TableSelect
      value={value}
      options={options}
      placeholder={"Select categories..."}
      onChange={values => onChange(values.map(v => v.value))}
      isMulti={true}
      isSearchable={true}
    />
  )
}
