/* eslint-disable i18next/no-literal-string */
import cn from "classnames"
import React, {PropsWithChildren} from "react"
import {AddProcessButton} from "../components/table/AddProcessButton"
import processesStyles from "../stylesheets/processes.styl"
import styles from "./processesTable.styl"

type Props = {
  allowAdd?: boolean,
  isSubprocess?: boolean,
}

export function ProcessTableTools(props: PropsWithChildren<Props>) {
  const {isSubprocess, allowAdd} = props

  return (
    <div className={styles.tools}>
      <Group expand>
        {props.children}
      </Group>
      <Group>
        {allowAdd && <AddProcessButton className={cn(processesStyles.tableFilter, styles.filterButton)} isSubprocess={isSubprocess}/>}
      </Group>
    </div>
  )
}

const Group = ({children, expand}: PropsWithChildren<{expand?: boolean}>) => !children ? null : (
  <div className={cn(styles.group, expand && styles.expand)}>
    {children}
  </div>
)
