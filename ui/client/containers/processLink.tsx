import React, {PropsWithChildren} from "react"
import {visualizationUrl} from "../common/VisualizationUrl"
import {ProcessId} from "../types"
import {PlainStyleLink} from "./plainStyleLink"

export function ProcessLink({processId,...props}: PropsWithChildren<{processId: ProcessId, className?: string, title?: string}>): JSX.Element {
  return (
    <PlainStyleLink to={visualizationUrl(processId)} {...props}/>
  )
}
