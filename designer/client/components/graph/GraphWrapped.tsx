import React, {forwardRef} from "react"
import {Process} from "../../types"
import {useWindows} from "../../windowManager"
import {Graph} from "./Graph"
import {useSelector} from "react-redux"
import {getUserSettings} from "../../reducers/selectors/userSettings"
import {ProcessCounts} from "../../reducers/graph"
import {
  injectNode,
  Layout,
  layoutChanged,
  nodeAdded,
  nodesConnected,
  nodesDisconnected,
  resetSelection,
  toggleSelection,
} from "../../actions/nk"
import {Capabilities} from "../../reducers/selectors/other"
import {ProcessType} from "../Process/types"
import {getProcessCategory, getSelectionState, isPristine} from "../../reducers/selectors/graph"
import {getLoggedUser, getProcessDefinitionData} from "../../reducers/selectors/settings"

export interface GraphProps {
  nodesConnected: typeof nodesConnected,
  nodesDisconnected: typeof nodesDisconnected,
  layoutChanged: typeof layoutChanged,
  injectNode: typeof injectNode,
  nodeAdded: typeof nodeAdded,
  resetSelection: typeof resetSelection,
  toggleSelection: typeof toggleSelection,

  processToDisplay: Process,
  divId: string,
  nodeIdPrefixForSubprocessTests: string,
  processCounts: ProcessCounts,
  capabilities: Capabilities,
  fetchedProcessDetails: ProcessType,
  layout: Layout,

  readonly?: boolean,
  nodeSelectionEnabled?: boolean,
  isDraggingOver?: boolean,
  isSubprocess?: boolean,

  connectDropTarget,
}

// Graph wrapped to make partial (for now) refactor to TS and hooks
export default forwardRef<Graph, GraphProps>(function GraphWrapped(props, forwardedRef): JSX.Element {
  const {openNodeWindow} = useWindows()
  const userSettings = useSelector(getUserSettings)
  const pristine = useSelector(isPristine)
  const processCategory = useSelector(getProcessCategory)
  const loggedUser = useSelector(getLoggedUser)
  const processDefinitionData = useSelector(getProcessDefinitionData)
  const selectionState = useSelector(getSelectionState)

  return (
    <Graph
      {...props}
      ref={forwardedRef}
      userSettings={userSettings}
      showModalNodeDetails={openNodeWindow}
      isPristine={pristine}
      processCategory={processCategory}
      loggedUser={loggedUser}
      processDefinitionData={processDefinitionData}
      selectionState={selectionState}
    />
  )
})
