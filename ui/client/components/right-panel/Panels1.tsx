import {PanelConfig} from "./PanelConfig"
import React from "react"
import {ExtractedPanel} from "./ExtractedPanel"
import {getEditPanel} from "./panelConfigs/GetEditPanel"
import {getTestPanel} from "./panelConfigs/GetTestPanel"
import {getGroupPanel} from "./panelConfigs/GetGroupPanel"
import {OwnProps as PanelOwnProps} from "./UserRightPanel"
import {RootState} from "../../reducers/index"
import ProcessUtils from "../../common/ProcessUtils"
import ProcessStateUtils from "../Process/ProcessStateUtils"
import {connect} from "react-redux"
import {EspActionsProps, mapDispatchWithEspActions} from "../../actions/ActionsUtils"
import {hot} from "react-hot-loader"

type OwnPropsPick = Pick<PanelOwnProps,
  | "capabilities"
  | "graphLayoutFunction"
  | "isStateLoaded"
  | "selectionActions"
  | "processState"
  | "exportGraph">

export function getConfig(own: OwnPropsPick, props: StateProps): PanelConfig[] {
  const {capabilities} = own
  const {actions, processId, isSubprocess, hasErrors} = props

  const {featuresSettings, processToDisplay} = props

  const {graphLayoutFunction, selectionActions} = own
  const {nodeToDisplay, history, undoRedoActions, keyActionsAvailable, selectionState} = props
  const editPanel = getEditPanel({
    actions,
    capabilities,
    graphLayoutFunction,
    hasErrors,
    history,
    isSubprocess,
    keyActionsAvailable,
    nodeToDisplay,
    processToDisplay,
    selectionActions,
    selectionState,
    undoRedoActions,
  })

  const {showRunProcessDetails, processIsLatestVersion, testCapabilities} = props
  const testPanel = getTestPanel({
    actions,
    capabilities,
    featuresSettings,
    isSubprocess,
    processId,
    processIsLatestVersion,
    processToDisplay,
    showRunProcessDetails,
    testCapabilities,
  })

  const {groupingState} = props
  const groupPanel = getGroupPanel({
    actions,
    capabilities,
    groupingState,
    nodeToDisplay,
  })

  return [editPanel, testPanel, groupPanel]
}

type OwnProps = OwnPropsPick

export function Panels(props: Props) {
  const panelConfigs = getConfig(props, props)

  return (
    <>
      {panelConfigs.map((panel) => <ExtractedPanel {...panel} key={panel.panelName}/>)}
    </>
  )
}

function mapState(state: RootState, props: OwnProps) {
  const {processState, isStateLoaded} = props
  const {graphReducer, ui, settings} = state

  const nothingToSave = ProcessUtils.nothingToSave(state)
  const processIsLatestVersion = graphReducer.fetchedProcessDetails?.isLatestVersion
  const processToDisplay = graphReducer.processToDisplay || {}
  const hasErrors = !ProcessUtils.hasNoErrors(processToDisplay)

  const fetchedProcessState = isStateLoaded ? processState : graphReducer.fetchedProcessDetails?.state
  const deployPossible = processIsLatestVersion && !hasErrors && nothingToSave && ProcessStateUtils.canDeploy(fetchedProcessState)
  return {
    isOpened: ui.rightPanelIsOpened,
    fetchedProcessDetails: graphReducer.fetchedProcessDetails,
    processId: graphReducer.fetchedProcessDetails?.name,
    versionId: graphReducer.fetchedProcessDetails?.processVersionId,
    processToDisplay,
    layout: graphReducer.layout || [], //TODO: now only needed for duplicate, maybe we can do it somehow differently?
    testCapabilities: graphReducer.testCapabilities || {},
    loggedUser: settings.loggedUser,
    nothingToSave,
    canExport: ProcessUtils.canExport(state),
    showRunProcessDetails: graphReducer.testResults || graphReducer.processCounts,
    keyActionsAvailable: ui.allModalsClosed,
    processIsLatestVersion,
    nodeToDisplay: graphReducer.nodeToDisplay,
    groupingState: graphReducer.groupingState,
    selectionState: graphReducer.selectionState,
    featuresSettings: settings.featuresSettings,
    isSubprocess: graphReducer.processToDisplay?.properties?.isSubprocess as boolean,
    businessView: graphReducer.businessView,
    history: graphReducer.history,
    saveDisabled: nothingToSave && processIsLatestVersion,

    hasErrors,
    fetchedProcessState,
    deployPossible,
  }
}

export type StateProps = EspActionsProps & ReturnType<typeof mapState>
export type Props = OwnProps & StateProps

export default connect(mapState, mapDispatchWithEspActions)(Panels)
