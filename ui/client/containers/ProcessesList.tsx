import {isEqual} from "lodash"
import React, {PropsWithChildren, useCallback, useEffect, useMemo, useState} from "react"
import {useSelector} from "react-redux"
import {ColumnsType} from "reactable"
import {useDebounce} from "use-debounce"
import {normalizeParams} from "../common/VisualizationUrl"
import {ProcessType} from "../components/Process/types"
import HttpService, {StatusesType} from "../http/HttpService"
import {getBaseIntervalTime} from "../reducers/selectors/settings"
import {useFetch} from "./hooks/useFetch"
import {useInterval} from "./Interval"
import styles from "./processesTable.styl"
import {ProcessesTable} from "./processesTable/ProcessesTable"
import {ProcessTableTools} from "./ProcessTableTools"
import {SearchQueryComponent} from "./SearchQuery"
import {FiltersState, SearchItem} from "./TableFilters"

export const getProcessState = (statuses?: StatusesType) => (process: ProcessType) => statuses?.[process.name] || null

type Queries = Partial<{
  isSubprocess: boolean,
  isArchived: boolean,
  isDeployed: boolean,
  isCustom: boolean,
}>
export type Filterable = (keyof ProcessType)[]
export type BaseProcessesOwnProps = PropsWithChildren<{
  defaultQuery: Queries,
  searchItems?: SearchItem[],

  sortable: string[],
  filterable: Filterable,
  columns: ColumnsType[],

  withStatuses?: boolean,
  allowAdd?: boolean,

  RowsRenderer: RowsRenderer,
}>

export type RowRendererProps = {
  processes: ProcessType[],
  getProcesses: () => void,
  statuses: StatusesType,
}
export type RowsRenderer = (props: RowRendererProps) => JSX.Element[]

function useIntervalRefresh(getProcesses: () => Promise<void>) {
  const refreshTime = useSelector(getBaseIntervalTime)
  useInterval(getProcesses, {refreshTime})
}

function useFilteredProcesses(filters: FiltersState & Queries) {
  const normalizedFilters = useMemo(() => filters && normalizeParams(filters), [filters])
  const [params] = useDebounce(normalizedFilters, 200, {equalityFn: isEqual})

  const fetchAction = useCallback(() => {
    if (params) {
      const {isCustom, ...rest} = params
      return isCustom ? HttpService.fetchCustomProcesses() : HttpService.fetchProcesses(rest)
    }
  }, [params])

  const [processes, getProcesses, isLoading] = useFetch(fetchAction, [])
  return {processes, getProcesses, isLoading}
}

function useFiltersState(defaultQuery: Queries) {
  const [_filters, _setFilters] = useState<FiltersState>(null)
  const setFilters = useCallback((value) => {
    _setFilters({...value, ...defaultQuery})
  }, [_setFilters, defaultQuery])

  const [search, filters] = useMemo(() => {
    if (_filters) {
      const {search, ...filters} = _filters
      return [search, filters]
    }
    return [null, null]
  }, [_filters])
  return {search, filters, setFilters}
}

const sortFunction = Intl.Collator().compare

export function ProcessesList(props: BaseProcessesOwnProps) {
  const {allowAdd, columns, RowsRenderer, filterable, defaultQuery, searchItems, sortable, withStatuses, children} = props

  const {search, filters, setFilters} = useFiltersState(defaultQuery)
  const {processes, getProcesses, isLoading} = useFilteredProcesses(filters)
  useIntervalRefresh(getProcesses)

  const [statuses, getStatuses] = useFetch(HttpService.fetchProcessesStates)
  useEffect(
    () => {
      if (withStatuses && processes.length) {
        getStatuses()
      }
    },
    [withStatuses, processes],
  )

  const filtered = useMemo(
    () => {
      const searchText = search?.toString().toLowerCase()
      return searchText ?
        processes.filter(p => filterable?.some(f => p[f]?.toString().includes(searchText))) :
        processes
    },
    [filterable, search, processes],
  )

  const elements = useMemo(
    () => RowsRenderer({processes: filtered, getProcesses, statuses}),
    [RowsRenderer, filtered, getProcesses, statuses],
  )

  return (
    <>
      <ProcessTableTools allowAdd={allowAdd} isSubprocess={defaultQuery.isSubprocess}>
        <SearchQueryComponent filters={searchItems} onChange={setFilters}/>
      </ProcessTableTools>

      {children}

      <ProcessesTable
        className={styles.table}
        isLoading={isLoading}
        sortable={sortable.map(column => ({column, sortFunction}))}
        columns={columns}
      >
        {elements}
      </ProcessesTable>
    </>
  )
}
