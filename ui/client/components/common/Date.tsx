import React from "react"
import {formatAbsolutely, formatRelatively} from "../../common/DateUtils"
import styles from "./Date.styl"

export default function Date({date}) {
  return (
    <span title={formatAbsolutely(date)} className={styles.date}>
      {formatRelatively(date)}
    </span>
  )
}
