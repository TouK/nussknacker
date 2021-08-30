import {cx} from "emotion"
import React from "react"

export function NodeLabel({label, className}: {label: string, className?: string}): JSX.Element {
  return <div className={cx("node-label", className)} title={label}>{label}:</div>
}
