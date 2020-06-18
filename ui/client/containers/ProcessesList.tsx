/* eslint-disable i18next/no-literal-string */
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
import {NkTable} from "./NkTable"
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
export type BaseProcessesOwnProps = PropsWithChildren<{
  defaultQuery: Queries,
  searchItems?: SearchItem[],

  sortable: string[],
  filterable: string[],
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

function useFilteredProcesses(filters: FiltersState & Queries) {
  const [params] = useDebounce(normalizeParams(filters), 200, {equalityFn: isEqual})

  const fetchAction = useCallback(() => {
    const {isCustom, ...rest} = params
    return isCustom ? HttpService.fetchCustomProcesses() : HttpService.fetchProcesses(rest)
  }, [params])

  const [processes, getProcesses, isLoading] = useFetch(fetchAction, [])

  const refreshTime = useSelector(getBaseIntervalTime)
  useInterval(getProcesses, refreshTime)
  return {processes, getProcesses, isLoading}
}

export function ProcessesList(props: BaseProcessesOwnProps) {
  const {allowAdd, columns, RowsRenderer, filterable, defaultQuery, searchItems, sortable, withStatuses} = props

  const [{search, ...filters}, setFilters] = useState<FiltersState>({})
  const {processes, getProcesses, isLoading} = useFilteredProcesses({...filters, ...defaultQuery})

  const [statuses, getStatuses] = useFetch(HttpService.fetchProcessesStates)
  useEffect(
    () => {
      if (withStatuses && processes.length) {
        getStatuses()
      }
    },
    [withStatuses, processes],
  )

  const elements = useMemo(
    () => RowsRenderer({processes, getProcesses, statuses}),
    [RowsRenderer, processes, getProcesses, statuses],
  )

  return (
    <>
      <ProcessTableTools allowAdd={allowAdd} isSubprocess={defaultQuery.isSubprocess}>
        <SearchQueryComponent filters={searchItems} onChange={setFilters}/>
      </ProcessTableTools>

      <NkTable
        isLoading={isLoading}
        filterBy={search?.toString()}

        itemsPerPage={10}

        sortable={sortable}
        filterable={filterable}
        columns={columns}
      >
        {elements}
      </NkTable>
    </>
  )
}
