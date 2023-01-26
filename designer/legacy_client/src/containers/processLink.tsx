import React, {PropsWithChildren, useContext} from "react"
import {ProcessId} from "../types"
import {PlainStyleLink} from "./plainStyleLink"
import {ScenariosContext} from "./ProcessTabs"

export function ProcessLink({
  processId,
  ...props
}: PropsWithChildren<{ processId: ProcessId, className?: string, title?: string }>): JSX.Element {
  const {scenarioLinkGetter} = useContext(ScenariosContext)
  return (
    <PlainStyleLink to={scenarioLinkGetter(processId)} {...props}/>
  )
}
