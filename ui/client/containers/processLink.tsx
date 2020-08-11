import {css} from "emotion"
import React, {PropsWithChildren} from "react"
import {Link} from "react-router-dom"
import {visualizationUrl} from "../common/VisualizationUrl"
import {ProcessId} from "../types"

export function ProcessLink({processId, children}: PropsWithChildren<{processId: ProcessId}>) {
  const style = css({
    "&, &:hover, &:focus": {
      color: "inherit",
    },
  })

  return (
    <Link className={style} to={visualizationUrl(processId)}>
      {children}
    </Link>
  )
}
