import React, {useCallback, useEffect, useMemo} from "react"
import {ProcessType} from "../../components/Process/types"
import HttpService, {StatusesType} from "../../http/HttpService"
import {useFetch} from "../hooks/useFetch"
import styles from "../processesTable.styl"
import {ProcessesTable} from "../processesTable/ProcessesTable"
import {ProcessTableTools} from "../ProcessTableTools"
import {SearchQueryComponent} from "../SearchQuery"
import {BaseProcessesOwnProps} from "./types"
import {useFilteredProcesses} from "./UseFilteredProcesses"
import {useFiltersState} from "./UseFiltersState"
import {useIntervalRefresh} from "./UseIntervalRefresh"
import {useTextFilter} from "./UseTextFilter"

export const getProcessState = (statuses?: StatusesType) => (process: ProcessType) => statuses?.[process.name] || null

export function ProcessesList(props: BaseProcessesOwnProps): JSX.Element {
  const {allowAdd, columns, RowsRenderer, filterable, defaultQuery, searchItems, sortable, withStatuses, children} = props

  const {search, filters, setFilters} = useFiltersState(defaultQuery)
  const {processes, getProcesses, isLoading} = useFilteredProcesses(filters)
  useIntervalRefresh(getProcesses)

  const fetchAction = useCallback(() => HttpService.fetchProcessesStates(), [])
  const [statuses, getStatuses] = useFetch(fetchAction)
  useEffect(
    () => {
      if (withStatuses && processes.length) {
        getStatuses()
      }
    },
    [withStatuses, processes],
  )

  const filtered = useTextFilter(search, processes, filterable)

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
        sortable={sortable.map(column => ({column, sortFunction: Intl.Collator().compare}))}
        columns={columns}
      >
        {elements}
      </ProcessesTable>
    </>
  )
}
