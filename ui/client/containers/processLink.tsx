import React, {PropsWithChildren} from "react"
import {visualizationUrl} from "../common/VisualizationUrl"
import {ProcessId} from "../types"
import {PlainStyleLink} from "./plainStyleLink"

export function ProcessLink({processId, children}: PropsWithChildren<{processId: ProcessId}>) {
  return (
    <PlainStyleLink to={visualizationUrl(processId)}>
      {children}
    </PlainStyleLink>
  )
}
