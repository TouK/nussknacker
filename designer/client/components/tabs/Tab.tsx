import React, {PropsWithChildren} from "react"
import styles from "./processTabs.styl"

export function Tab({title}: PropsWithChildren<{title: string}>) {
  return (
    <div className={styles.tab}>
      <span className={styles.text}>{title}</span>
    </div>
  )
}
