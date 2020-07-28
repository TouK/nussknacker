import {css, cx} from "emotion"
import {isEmpty} from "lodash"
import React, {PropsWithChildren} from "react"
import processesTableStyles from "../../containers/processesTable.styl"
import {useNkTheme} from "../../containers/theme"
import bootstrapStyles from "../../stylesheets/bootstrap.styl"
import processesStyles from "../../stylesheets/processes.styl"
import SvgDiv from "../SvgDiv"
import {InputWithFocus} from "../withFocus"
import {FilterProps} from "./FilterTypes"
import searchIconStyles from "./searchIcon.styl"

type InputProps = FilterProps & {
  placeholder?: string,
  className?: string,
}

function ThemedInput({value, onChange, placeholder, className, children}: PropsWithChildren<InputProps>) {
  const {theme} = useNkTheme()
  return (
    <div className={cx(children && searchIconStyles.withAddon)}>
      <InputWithFocus
        type="text"
        placeholder={placeholder}
        className={cx(bootstrapStyles.formControl, css({
          color: theme?.colors?.neutral90,
          height: theme?.spacing?.controlHeight,
        }), className)}
        value={value || ""}
        onChange={e => onChange(`${e.target.value}`)}
      />
      {children && (
        <div className={cx(searchIconStyles.addon)}>{children}</div>
      )}
    </div>
  )
}

function AddonIcon(props: {className?: string, svg: string}) {
  return <SvgDiv className={cx(searchIconStyles.icon, props.className)} svgFile={props.svg}/>
}

function SearchIcon(props: {isEmpty: boolean}) {
  return <AddonIcon svg="search.svg" className={props.isEmpty ? searchIconStyles.searchIconFill : searchIconStyles.searchIconFillFilter}/>
}

function TableFilter(props: PropsWithChildren<{className?: string}>) {
  return (
    <div className={cx(processesStyles.tableFilter, props.className)}>
      {props.children}
    </div>
  )
}

function SearchFilter(props: FilterProps) {
  return (
    <TableFilter className={processesTableStyles.filterInput}>
      <ThemedInput {...props} placeholder="Filter by text...">
        <SearchIcon isEmpty={isEmpty(props.value)}/>
      </ThemedInput>
    </TableFilter>
  )
}

export default SearchFilter
