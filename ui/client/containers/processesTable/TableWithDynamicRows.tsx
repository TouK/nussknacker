import React, {Children, useMemo} from "react"
import {TableComponentProperties} from "reactable"
import {CountRowsToFill} from "./CountRowsToFill"
import {TableWithDefaults} from "./TableWithDefaults"
import {useRowsPerPageState} from "./useRowsPerPage"

export function TableWithDynamicRows(props: TableComponentProperties) {
  const {children, currentPage = 0} = props
  const allRowsCount = useMemo(() => Children.count(children), [children])
  const [rowsPerPage, rowsOnCurrentPage, setRowsPerPage] = useRowsPerPageState(allRowsCount, currentPage)
  const itemsPerPage = rowsOnCurrentPage === allRowsCount ? 0 : rowsPerPage

  return (
    <CountRowsToFill items={rowsOnCurrentPage} onChange={setRowsPerPage}>
      <TableWithDefaults
        {...props}
        itemsPerPage={itemsPerPage}
      />
    </CountRowsToFill>
  )
}
