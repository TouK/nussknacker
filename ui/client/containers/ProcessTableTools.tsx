/* eslint-disable i18next/no-literal-string */
import React, {PropsWithChildren} from "react"
import {AddProcessButton} from "../components/table/AddProcessButton"
import styles from "./processesTable.styl"

type Props = {
  allowAdd?: boolean,
  isSubprocess?: boolean,
}

export function ProcessTableTools(props: PropsWithChildren<Props>) {
  const {isSubprocess, allowAdd} = props

  return (
    <>
      <div id="process-top-bar" className={styles.tools}>
        {props.children}
        {allowAdd && <AddProcessButton isSubprocess={isSubprocess}/>}
      </div>
    </>
  )
}

