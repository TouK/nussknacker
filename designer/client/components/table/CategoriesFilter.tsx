import React from "react"
import {useSelector} from "react-redux"
import {getFilterCategories} from "../../reducers/selectors/settings"
import {ValueFieldProps} from "../valueField"
import TableSelect, {OptionType} from "./TableSelect"
import {useParseValues} from "./useParseValue"

export function CategoriesFilter(props: ValueFieldProps<string[]>): JSX.Element {
  const {onChange} = props
  const options: OptionType<string>[] = useSelector(getFilterCategories)
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
