import {PanelConfig} from "./PanelConfig"
import React from "react"
import {ExtractedPanel} from "./ExtractedPanel"
import {getTestPanel} from "./panelConfigs/GetTestPanel"
import {getGroupPanel} from "./panelConfigs/GetGroupPanel"
import {OwnProps as PanelOwnProps} from "./UserRightPanel"
import {RootState} from "../../reducers/index"
import ProcessUtils from "../../common/ProcessUtils"
import ProcessStateUtils from "../Process/ProcessStateUtils"
import {connect} from "react-redux"
import {EspActionsProps, mapDispatchWithEspActions} from "../../actions/ActionsUtils"
import {areAllModalsClosed, isRightPanelOpened} from "./selectors-ui"
import {
  isPristine,
  isLatestProcessVersion,
  getProcessToDisplay,
  hasError,
  getFetchedProcessDetails,
  getProcessId,
  getProcessVersionId,
  getNodeToDisplay,
  getFeatureSettings,
  isSubprocess,
  isBusinessView,
  getHistory,
} from "./selectors"

type OwnPropsPick = Pick<PanelOwnProps,
  | "capabilities"
  | "graphLayoutFunction"
  | "isStateLoaded"
  | "selectionActions"
  | "processState"
  | "exportGraph">

export function getConfig(own: OwnPropsPick, props: StateProps): PanelConfig[] {
  const {capabilities} = own
  const {processId, isSubprocess, featuresSettings, processToDisplay, nodeToDisplay, showRunProcessDetails, processIsLatestVersion, testCapabilities} = props
  const {actions} = props
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

  return [testPanel, groupPanel]
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
  const {graphReducer, settings} = state

  const nothingToSave = isPristine(state)
  const processIsLatestVersion = isLatestProcessVersion(state)
  const processToDisplay = getProcessToDisplay(state)
  const hasErrors = hasError(state)

  const fetchedProcessState = isStateLoaded ? processState : graphReducer.fetchedProcessDetails?.state
  const deployPossible = processIsLatestVersion && !hasErrors && nothingToSave && ProcessStateUtils.canDeploy(fetchedProcessState)
  return {
    isOpened: isRightPanelOpened(state),
    fetchedProcessDetails: getFetchedProcessDetails(state),
    processId: getProcessId(state),
    versionId: getProcessVersionId(state),
    processToDisplay,
    layout: graphReducer.layout || [], //TODO: now only needed for duplicate, maybe we can do it somehow differently?
    testCapabilities: graphReducer.testCapabilities || {},
    loggedUser: settings.loggedUser,
    nothingToSave,
    canExport: ProcessUtils.canExport(state),
    showRunProcessDetails: graphReducer.testResults || graphReducer.processCounts,
    keyActionsAvailable: areAllModalsClosed(state),
    processIsLatestVersion,
    nodeToDisplay: getNodeToDisplay(state),
    groupingState: graphReducer.groupingState,
    selectionState: graphReducer.selectionState,
    featuresSettings: getFeatureSettings(state),
    isSubprocess: isSubprocess(state),
    businessView: isBusinessView(state),
    history: getHistory(state),
    saveDisabled: nothingToSave && processIsLatestVersion,

    hasErrors,
    fetchedProcessState,
    deployPossible,
  }
}

export type StateProps = EspActionsProps & ReturnType<typeof mapState>
export type Props = OwnProps & StateProps

export default connect(mapState, mapDispatchWithEspActions)(Panels)
