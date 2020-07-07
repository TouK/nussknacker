import React, {PropsWithChildren} from "react"
import styles from "./processTabs.styl"

export function TabContent({children}: PropsWithChildren<{}>) {
  return (
    <div className={styles.content}>
      {children}
    </div>
  )
}
