import {isArray, isString} from "lodash"
import React, {PropsWithChildren, useCallback, useMemo} from "react"
import {useTranslation} from "react-i18next"
import {SortType, TableComponentProperties} from "reactable"
import LoaderSpinner from "../../components/Spinner"
import {UnknownRecord} from "../../types/common"
import {useSearchQuery} from "../hooks/useSearchQuery"
import {TableWithDynamicRows} from "./TableWithDynamicRows"

type OwnProps = {
  isLoading?: boolean,
  className?: string,
}

type TableProps = Pick<TableComponentProperties, "columns" | "filterable" | "sortable" | "filterBy" | "itemsPerPage" | "children">
type Props = TableProps & OwnProps

type QueryType = {page: number} & SortType

function HorizontalScroll({children}: PropsWithChildren<UnknownRecord>) {
  return (
    <div style={{overflow: "auto", display: "flex", flex: 1}}>
      {children}
    </div>
  )
}

export function ProcessesTable(props: Props) {
  const {isLoading, sortable, columns, ...passProps} = props

  const {t} = useTranslation()

  const options = useMemo(() => ({parseNumbers: true}), [])
  const [query, setQuery] = useSearchQuery<QueryType>(options)

  const defaultSortColummn = useMemo(
    () => {
      const [firstColumn] = sortable && isArray(sortable) ? sortable : columns
      // eslint-disable-next-line i18next/no-literal-string
      return isString(firstColumn) ? firstColumn : firstColumn["column"] || firstColumn["key"]
    },
    [sortable, columns],
  )

  const {page = 0, direction = 1, column = defaultSortColummn} = query

  const onPageChange = useCallback(page => setQuery({...query, page}), [query])
  const onSort = useCallback(({column, direction}) => setQuery({...query, column, direction}), [query])

  return (
    <>
      <LoaderSpinner show={isLoading}/>
      <HorizontalScroll>
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
      </HorizontalScroll>
    </>
  )
}
