import {useCallback, useMemo, useState} from "react"

export function useRowsPerPageState(rowsCount: number, page: number): [number, number, (c: number) => void] {
  const [rowsPerPage, setRowsPerPage] = useState(5)

  const pagesCount = useMemo(() => Math.ceil(rowsCount / rowsPerPage) || 0, [rowsCount, rowsPerPage])
  const isLastPage = useMemo(() => pagesCount > 1 && page + 1 >= pagesCount, [page, pagesCount])

  const rowsOnCurrentPage = useMemo(() => {
    if (pagesCount) {
      const rowsOnLastPage = rowsCount - rowsPerPage * (pagesCount - 1)
      return isLastPage ? rowsOnLastPage : rowsPerPage
    }
  }, [page, rowsCount, rowsPerPage])

  const updateRowsPerPage = useCallback(
    (count: number) => {
      if (!isLastPage || count >= rowsPerPage) {
        setRowsPerPage(Math.min(count, rowsCount))
      }
    },
    [rowsCount, isLastPage],
  )

  return [rowsPerPage, rowsOnCurrentPage, updateRowsPerPage]
}
