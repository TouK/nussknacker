import cn from "classnames"
import {isEmpty} from "lodash"
import React, {PropsWithChildren} from "react"
import processesTableStyles from "../../containers/processesTable.styl"
import processesStyles from "../../stylesheets/processes.styl"
import SvgDiv from "../SvgDiv"
import {InputWithFocus} from "../withFocus"
import {FilterProps} from "./FilterTypes"
import searchIconStyles from "./searchIcon.styl"

type InputProps = FilterProps & {
  placeholder?: string,
  className?: string,
}

function Input({value, onChange, placeholder, className, children}: PropsWithChildren<InputProps>) {
  return (
    <div className={cn(children && searchIconStyles.withAddon)}>
      <InputWithFocus
        type="text"
        placeholder={placeholder}
        className={cn(processesStyles.formControl, className)}
        value={value || ""}
        onChange={e => onChange(`${e.target.value}`)}
      />
      {children && (
        <div className={cn(searchIconStyles.addon)}>{children}</div>
      )}
    </div>
  )
}

function AddonIcon(props: {className?: string, svg: string}) {
  return <SvgDiv className={cn(searchIconStyles.icon, props.className)} svgFile={props.svg}/>
}

function SearchIcon(props: {isEmpty: boolean}) {
  return <AddonIcon svg="search.svg" className={props.isEmpty ? searchIconStyles.searchIconFill : searchIconStyles.searchIconFillFilter}/>
}

function TableFilter(props: PropsWithChildren<{className?: string}>) {
  return (
    <div className={cn(processesStyles.tableFilter, props.className)}>
      {props.children}
    </div>
  )
}

function SearchFilter(props: FilterProps) {
  return (
    <TableFilter className={processesTableStyles.filterInput}>
      <Input {...props} placeholder="Filter by text...">
        <SearchIcon isEmpty={isEmpty(props.value)}/>
      </Input>
    </TableFilter>
  )
}

export default SearchFilter
