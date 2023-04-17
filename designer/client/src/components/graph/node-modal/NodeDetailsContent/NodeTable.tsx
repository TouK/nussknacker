import React, {PropsWithChildren} from "react"
import {cx} from "@emotion/css"

export function NodeTable({
  children,
  className,
  editable,
}: PropsWithChildren<{ className?: string, editable?: boolean }>): JSX.Element {
  return (
    <div className={cx("node-table", {"node-editable": editable}, className)}>
      {children}
    </div>
  )
}

export function NodeTableBody({children, className}: PropsWithChildren<{ className?: string }>): JSX.Element {
  return (
    <div className={cx("node-table-body", className)}>
      {children}
    </div>
  )
}
