import {css} from "emotion"
import React, {useMemo} from "react"
import {useTranslation} from "react-i18next"

export function TableItemsCount(props: {page: number, items: number, rows: number}): JSX.Element {
  const {items, rows, page} = props
  if (rows <= 0) {
    return null
  }

  const {t} = useTranslation()
  const text = useMemo(
    () => {
      const first = page * rows + 1
      const last = (page + 1) * rows
      return t('table.itemsCount', `{{first}} to {{last}} of total {{total}}`, {
        first,
        last: Math.min(last, items),
        total: items,
      })
    },
    [page, rows, items],
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

  return (
    <div className={styles}>
      <span>{text}</span>
    </div>
  )
}
