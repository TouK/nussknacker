import {PropsWithChildren} from "react"
import {ColumnsType} from "reactable"
import {ProcessType} from "../../components/Process/types"
import {StatusesType} from "../../http/HttpService"
import {SearchItem} from "../TableFilters"

export type Queries = Partial<{
  isSubprocess: boolean,
  isArchived: boolean,
  isDeployed: boolean,
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
