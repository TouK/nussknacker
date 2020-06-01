import React, {PropsWithChildren} from "react"
import styles from "./ProcessTabs.styl"

export function Tab({title}: PropsWithChildren<{ title: string }>) {
  return (
    <div className={styles.tab}>{title}</div>
  )
}
