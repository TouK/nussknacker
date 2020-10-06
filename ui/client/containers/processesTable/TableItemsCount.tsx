import {css} from "emotion"
import React, {useMemo} from "react"
import {useTranslation} from "react-i18next"
import {useRowsOnCurrentPage} from "./useRowsPerPage"

type Props = {
  page: number,
  total: number,
  rows: number,
}

export function TableItemsCount(props: Props): JSX.Element {
  const {total, rows, page} = props

  const {t} = useTranslation()
  const [, first, last] = useRowsOnCurrentPage(total, rows, page)
  const text = useMemo(
    () => t("table.itemsCount", "{{first}} to {{last}} of total {{total}}", {first, last, total}),
    [t, first, last, total],
  )

  const styles = useMemo(() => css({
    position: "absolute",
    top: 0,
    right: 0,
    bottom: 0,
    display: "flex",
    alignItems: "center",
    margin: "0 0.5em",
  }), [])

  return rows <= 0 ? null : (
    <div className={styles}>
      <span>{text}</span>
    </div>
  )
}
