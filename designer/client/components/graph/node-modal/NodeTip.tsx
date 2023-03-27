import React, {ReactElement} from "react"
import {cx} from "@emotion/css"

export default function NodeTip({className, icon, title}: { className?: string, icon: ReactElement, title: string }) {
  return (
    <div className={cx("node-tip", className)} title={title}>{icon}</div>
  )
}
