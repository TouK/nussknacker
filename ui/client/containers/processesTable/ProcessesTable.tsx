import {isArray, isString} from "lodash"
import React, {useCallback, useMemo} from "react"
import {useTranslation} from "react-i18next"
import {SortType, TableComponentProperties} from "reactable"
import LoaderSpinner from "../../components/Spinner"
import {useSearchQuery} from "../hooks/useSearchQuery"
import {TableWithDynamicRows} from "./TableWithDynamicRows"

type OwnProps = {
  isLoading?: boolean,
  className?: string,
}

type TableProps = Pick<TableComponentProperties, "columns" | "filterable" | "sortable" | "filterBy" | "itemsPerPage" | "children">
type Props = TableProps & OwnProps

type QueryType = {page: number} & SortType

export function ProcessesTable(props: Props) {
  const {isLoading, sortable, columns, ...passProps} = props

  const {t} = useTranslation()

  const [query, setQuery] = useSearchQuery<QueryType>({parseNumbers: true})

  const defaultSortColummn = useMemo(
    () => {
      const [firstColumn] = sortable && isArray(sortable) ? sortable : columns
      return isString(firstColumn) ? firstColumn : firstColumn.key
    },
    [sortable, columns],
  )

  const {page = 0, direction = 1, column = defaultSortColummn} = query

  const onPageChange = useCallback(page => setQuery({...query, page}), [query])
  const onSort = useCallback(({column, direction}) => setQuery({...query, column, direction}), [query])

  return (
    <>
      <LoaderSpinner show={isLoading}/>
      <TableWithDynamicRows
        {...passProps}
        noDataText={isLoading ? t("table.loading", "Loading data...") : t("table.noData", "No matching records found.")}
        onPageChange={onPageChange}
        currentPage={page}
        sortable={sortable}
        columns={columns}
        sortBy={{column, direction}}
        onSort={onSort}
      />
    </>
  )
}
