import cn from "classnames"
import React, {useCallback} from "react"
import styles from "../../containers/processesTable.styl"
import "../../stylesheets/processes.styl"
import {ThemedSelect} from "../themed/ThemedSelect"

export type OptionType<T> = {
  label: string,
  value?: T,
}

type Common<T> = {
  isSearchable: boolean,
  placeholder: string,
  options: OptionType<T>[],
}

type Single<T> = {
  isMulti: false,
  value: OptionType<T>,
  onChange: (value: OptionType<T>) => void,
}

type Multi<T> = {
  isMulti: true,
  value: OptionType<T>[],
  onChange: (value: OptionType<T>[]) => void,
}

type Props<T> = Common<T> & (Single<T> | Multi<T>)

export default function TableSelect<T>(props: Props<T>): JSX.Element {
  const {onChange, isMulti} = props

  const getOnChange = useCallback(value => {
    onChange(isMulti ? value || [] : value)
  }, [onChange, isMulti])

  return (
    <div className={cn("table-filter", "input-group", styles.filterInput)}>
      <ThemedSelect
        {...props}
        closeMenuOnSelect={false}
        onChange={getOnChange}
      />
    </div>
  )
}
