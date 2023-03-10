import React from "react"
import {cx} from "@emotion/css"

export default function NodeTip({className, icon, title}: { className?: string, icon: string, title: string }) {
  return (
    <div
      className={cx("node-tip", className)}
      title={title}
      dangerouslySetInnerHTML={{__html: icon}}
    />
  )
}
