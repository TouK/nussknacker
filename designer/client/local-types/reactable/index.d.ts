// Type definitions for reactable 0.14
// Project: https://github.com/abdulrahman-khankan/reactable
// Definitions by: Christoph Spielmann <https://github.com/spielc>, Priscila Moneo <https://github.com/priscila-moneo>
// Definitions: https://github.com/DefinitelyTyped/DefinitelyTyped
// TypeScript Version: 2.8

import * as React from "react"
import {UnknownRecord} from "../../types/common"

export interface KeyLabelObject {
  key: string,
  label: string,
}

export type ColumnsType = string | KeyLabelObject

export type SortDirection = "asc" | "desc" | 1 | -1

export type FilterMethodType = (text: string) => void

export type SortType = {column: string, direction: SortDirection}

export type SortFunction = (a: string, b: string) => number
export type ColumnSort = {column: string, sortFunction: SortFunction}

export interface TableComponentProperties<T = any> extends React.PropsWithChildren<UnknownRecord> {
  data?: T[],
  className?: string,
  columns?: ColumnsType[],
  defaultSort?: SortType,
  id?: string,
  sortable?: ColumnSort[] | string[] | boolean,
  sortBy?: SortType,
  filterable?: string[],
  filterBy?: string,
  onFilter?: FilterMethodType,
  itemsPerPage?: number,
  noDataText?: string,
  pageButtonLimit?: number,
  currentPage?: number,
  hideFilterInput?: boolean,
  previousPageLabel?: string,
  nextPageLabel?: string,
  onSort?: (sort: SortType) => void,
  onPageChange?: (page: number) => void,
  hidden?: boolean,
}

export interface ThProperties {
  column: string,
  className?: string,
}

export interface TrProperties<T> {
  data?: T,
  className?: string,
}

export interface TdProperties {
  column: string,
  value?: any,
  data?: any,
  className?: string,
}

export class Table<T> extends React.Component<TableComponentProperties<T>> {
}

export class Thead extends React.Component {
}

export class Th extends React.Component<ThProperties> {
}

export class Tr<T> extends React.Component<TrProperties<T>> {
}

export class Td extends React.Component<TdProperties> {
}

export class Tfoot extends React.Component {
}

