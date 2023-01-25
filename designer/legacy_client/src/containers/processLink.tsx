import React, {PropsWithChildren} from "react"
import {ProcessId} from "../types"
import {PlainStyleLink} from "./plainStyleLink"

export function ProcessLink({processId,...props}: PropsWithChildren<{processId: ProcessId, className?: string, title?: string}>): JSX.Element {
  return (
    <PlainStyleLink to={`/visualization/${encodeURIComponent(processId)}`} {...props}/>
  )
}
