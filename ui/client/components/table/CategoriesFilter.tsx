import TableSelect from "./TableSelect"
import React from "react"
import {useSelector} from "react-redux"
import {getFilterCategories} from "../../reducers/selectors/settings"
import {MultiSelectFilterProps} from "./FilterTypes"
import {useParseValues} from "./useParseValue"

export function CategoriesFilter(props: MultiSelectFilterProps<string>) {
  const {onChange} = props
  const options = useSelector(getFilterCategories)
  const value = useParseValues(options, props.value)
  return (
    <TableSelect
      defaultValue={value}
      options={options}
      placeholder={"Select categories..."}
      onChange={values => onChange(values.map(v => v.value))}
      isMulti={true}
      isSearchable={true}
    />
  )
}
