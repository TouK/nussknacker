import React, {Children, useMemo} from "react"
import {TableComponentProperties} from "reactable"
import {FillCheck} from "./FillCheck"
import {TableItemsCount} from "./TableItemsCount"
import {TableElementsSelectors, TableWithDefaults} from "./TableWithDefaults"
import {useRowsOnCurrentPage, useRowsPerPageState} from "./useRowsPerPage"

export function TableWithDynamicRows(props: TableComponentProperties): JSX.Element {
  const {children, currentPage = 0} = props
  const totalRows = useMemo(() => Children.count(children), [children])
  const [rowsPerPage, calcRowsPerPage] = useRowsPerPageState(currentPage, totalRows)
  const [rowsOnCurrentPage] = useRowsOnCurrentPage(totalRows, rowsPerPage, currentPage)
  const itemsPerPage = useMemo(() => rowsOnCurrentPage >= totalRows ? 0 : rowsPerPage, [rowsOnCurrentPage, rowsPerPage, totalRows])

  return (
    <FillCheck onChange={calcRowsPerPage}>
      <TableWithDefaults
        {...props}
        itemsPerPage={itemsPerPage}
        extensions={{
          [TableElementsSelectors.pagination]: <TableItemsCount rows={rowsPerPage} page={currentPage} total={totalRows}/>,
        }}
      />
    </FillCheck>
  )
}
