/* eslint-disable i18next/no-literal-string */
import React from "react"
import {ExtractedPanel} from "../ExtractedPanel"
import {OwnProps as PanelOwnProps} from "../UserRightPanel"
import {RootState} from "../../../reducers/index"
import ProcessUtils from "../../../common/ProcessUtils"
import ProcessStateUtils from "../../Process/ProcessStateUtils"
import {connect} from "react-redux"
import * as DialogMessages from "../../../common/DialogMessages"
import HttpService from "../../../http/HttpService"
import history from "../../../history"
import {Archive} from "../../../containers/Archive"
import {events} from "../../../analytics/TrackingEvents"
import Dialogs from "../../modals/Dialogs"
import InlinedSvgs from "../../../assets/icons/InlinedSvgs"
import {get, isEmpty} from "lodash"
import {exportProcessToJSON, exportProcessToPdf, importFiles} from "../../../actions/nk/importExport"
import {reportEvent} from "../../../actions/nk/reportEvent"
import {toggleConfirmDialog} from "../../../actions/nk/ui/toggleConfirmDialog"
import {toggleModalDialog} from "../../../actions/nk/modal"
import {bindActionCreators} from "redux"
import {
  hasError,
  getProcessToDisplay,
  getFetchedProcessDetails,
  getProcessId,
  getProcessVersionId,
  isBusinessView,
  getFeatureSettings,
  getFetchedProcessState,
  isDeployPossible,
  isSaveDisabled,
} from "../selectors"

type OwnPropsPick = Pick<PanelOwnProps,
  | "capabilities"
  | "isStateLoaded"
  | "processState"
  | "exportGraph">

type OwnProps = OwnPropsPick
export type Props = OwnProps & StateProps

export function ProcessPanel(props: Props) {
  const {
    capabilities, exportGraph, processId, deployPossible, saveDisabled, fetchedProcessState, featuresSettings,
    processToDisplay, fetchedProcessDetails, businessView, versionId, canExport,
    exportProcessToJSON, exportProcessToPdf, importFiles, reportEvent, toggleConfirmDialog, toggleModalDialog,
  } = props

  const isRunning = () => ProcessStateUtils.isRunning(fetchedProcessState)

  const archiveProcess = () => !isRunning() && toggleConfirmDialog(
    true,
    DialogMessages.archiveProcess(processId),
    () => HttpService.archiveProcess(processId).then(() => history.push(Archive.path)),
    "Yes",
    "No",
    {category: events.categories.rightPanel, action: events.actions.buttonClick, name: "archive"},
  )

  const migrate = () => toggleConfirmDialog(
    true,
    DialogMessages.migrate(processId, featuresSettings.remoteEnvironment.targetEnvironmentId),
    () => HttpService.migrateProcess(processId, versionId),
    "Yes",
    "No",
    {category: events.categories.rightPanel, action: events.actions.buttonClick, name: "migrate"},
  )

  const importFromFiles = (files) => importFiles(files, processId)

  //TODO: Checking permission to archiwization should be done by check action from state - we should add new action type
  const compareVersions = () => toggleModalDialog(Dialogs.types.compareVersions)
  const save = () => toggleModalDialog(Dialogs.types.saveProcess)
  const hasOneVersion = () => get(fetchedProcessDetails, "history", []).length <= 1
  const exportToJSON = () => exportProcessToJSON(processToDisplay, versionId)
  const exportToPdf = () => exportProcessToPdf(processId, versionId, exportGraph(), businessView)

  const panelConfigs = {
    panelName: "Process",
    buttons: [
      {
        name: `save${!saveDisabled ? "*" : ""}`,
        isHidden: !capabilities.write,
        disabled: saveDisabled,
        onClick: save,
        icon: InlinedSvgs.buttonSave,
      },
      {
        name: "migrate",
        isHidden: !capabilities.deploy || isEmpty(featuresSettings?.remoteEnvironment),
        disabled: !deployPossible,
        onClick: migrate,
        icon: InlinedSvgs.buttonMigrate,
      },
      {
        name: "compare",
        onClick: compareVersions,
        icon: "compare.svg",
        disabled: hasOneVersion(),
      },
      {
        name: "import",
        isHidden: !capabilities.write,
        disabled: false,
        onClick: () => reportEvent({
          category: events.categories.rightPanel,
          action: events.actions.buttonClick,
          name: "import",
        }),
        onDrop: importFromFiles,
        icon: InlinedSvgs.buttonImport,
      },
      {
        name: "JSON",
        disabled: !canExport,
        onClick: exportToJSON,
        icon: InlinedSvgs.buttonExport,
      },
      {
        name: "PDF",
        disabled: !canExport,
        onClick: exportToPdf,
        icon: InlinedSvgs.pdf,
      },
      {
        name: "archive",
        onClick: archiveProcess,
        disabled: isRunning(),
        icon: "archive.svg",
        isHidden: !capabilities.write,
      },
    ],
  }

  return (
    <>
      {[panelConfigs].map((panel) => <ExtractedPanel {...panel} key={panel.panelName}/>)}
    </>
  )
}

const mapState = (state: RootState, props: OwnProps) => ({
  fetchedProcessDetails: getFetchedProcessDetails(state),
  processId: getProcessId(state),
  versionId: getProcessVersionId(state),
  processToDisplay: getProcessToDisplay(state),
  canExport: ProcessUtils.canExport(state),
  featuresSettings: getFeatureSettings(state),
  businessView: isBusinessView(state),
  saveDisabled: isSaveDisabled(state),
  hasErrors: hasError(state),
  fetchedProcessState: getFetchedProcessState(state, props),
  deployPossible: isDeployPossible(state, props),
})

const mapDispatch = (dispatch) => bindActionCreators({
  exportProcessToJSON,
  exportProcessToPdf,
  importFiles,
  reportEvent,
  toggleConfirmDialog,
  toggleModalDialog,
}, dispatch)

export type StateProps = ReturnType<typeof mapDispatch> & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(ProcessPanel)
