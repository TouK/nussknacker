import {isArray, isString} from "lodash"
import React, {useCallback, useMemo} from "react"
import {SortType, Table, TableComponentProperties} from "reactable"
import LoaderSpinner from "../../components/Spinner"
import {useSearchQuery} from "../hooks/useSearchQuery"

type OwnProps = {
  isLoading?: boolean,
  className?: string,
}

type TableProps = Pick<TableComponentProperties, "columns" | "filterable" | "sortable" | "filterBy" | "itemsPerPage" | "children">
type Props = TableProps & OwnProps

type QueryType = {page: number} & SortType

export function ProcessesTable(props: Props) {
  const {isLoading, sortable, columns, className, ...passProps} = props

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
      <Table
        {...passProps}
        className={"esp-table"}
        noDataText={isLoading ? "Loading data..." : "No matching records found."}
        previousPageLabel={"<"}
        nextPageLabel={">"}
        pageButtonLimit={5}
        hideFilterInput={true}

        itemsPerPage={5}

        sortable={sortable}
        columns={columns}

        currentPage={page}
        onPageChange={onPageChange}
        sortBy={{column, direction}}
        onSort={onSort}
      />
    </>
  )
}
