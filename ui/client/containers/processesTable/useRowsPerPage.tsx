import {useCallback, useMemo, useState} from "react"
import {rafThrottle} from "../../components/graph/rafThrottle"

export function useRowsOnCurrentPage(total: number, rowsPerPage: number, currentPage: number): [number, number, number] {
  const pagesCount = useMemo(() => Math.ceil(total / rowsPerPage) || 0, [total, rowsPerPage])
  const isLastPage = useMemo(() => pagesCount > 1 && currentPage + 1 >= pagesCount, [currentPage, pagesCount])

  const rowsOnCurrentPage = useMemo(() => {
    if (pagesCount) {
      const rowsOnLastPage = total - rowsPerPage * (pagesCount - 1)
      return isLastPage ? rowsOnLastPage : rowsPerPage
    }
  }, [total, rowsPerPage, isLastPage, pagesCount])

  const firstItem = useMemo(() => currentPage * rowsPerPage + 1, [currentPage, rowsPerPage])
  const lastItem = useMemo(() => firstItem + rowsOnCurrentPage - 1, [firstItem, rowsOnCurrentPage])

  return [rowsOnCurrentPage, firstItem, lastItem]
}

export function useRowsPerPageState(currentPage: number, totalRows: number): [number, (freeSpace: number, currentHeight: number) => void] {
  const [rowsPerPage, _setRowsPerPage] = useState(8)

  const _calcRowsPerPage = useCallback(
    (currentHeight: number, freeSpace: number) => (currentRowsPerPage: number) => {
      const rowsTilCurrentPage = currentRowsPerPage * currentPage
      const rowsAvailableForNextPages = Math.max(totalRows - rowsTilCurrentPage - currentRowsPerPage, 0)

      const avgRowSize = Math.max(currentHeight / currentRowsPerPage, 40)
      const placeForRows = Math.round(freeSpace / avgRowSize)
      const change = Math.min(placeForRows, rowsAvailableForNextPages)
      return change ? Math.max(currentRowsPerPage + change, 1) : currentRowsPerPage
    },
    [currentPage, totalRows],
  )

  const calcRowsPerPage = useCallback(
    rafThrottle((freeSpace: number, currentHeight: number) => {
      _setRowsPerPage(_calcRowsPerPage(currentHeight, freeSpace))
    }),
    [_calcRowsPerPage],
  )
  return [rowsPerPage, calcRowsPerPage]
}
