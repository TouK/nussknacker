/* eslint-disable i18next/no-literal-string */
import React from "react"
import {OwnProps as PanelOwnProps} from "../UserRightPanel"
import {RootState} from "../../../reducers/index"
import ProcessUtils from "../../../common/ProcessUtils"
import {connect} from "react-redux"
import * as DialogMessages from "../../../common/DialogMessages"
import HttpService from "../../../http/HttpService"
import history from "../../../history"
import {Archive} from "../../../containers/Archive"
import {events} from "../../../analytics/TrackingEvents"
import Dialogs from "../../modals/Dialogs"
import InlinedSvgs from "../../../assets/icons/InlinedSvgs"
import {isEmpty} from "lodash"
import {exportProcessToJSON, exportProcessToPdf, importFiles} from "../../../actions/nk/importExport"
import {reportEvent} from "../../../actions/nk/reportEvent"
import {toggleConfirmDialog} from "../../../actions/nk/ui/toggleConfirmDialog"
import {toggleModalDialog} from "../../../actions/nk/modal"
import {bindActionCreators} from "redux"
import {
  hasError,
  getProcessToDisplay,
  getProcessId,
  getProcessVersionId,
  isBusinessView,
  getFeatureSettings,
  isDeployPossible,
  isSaveDisabled,
  hasOneVersion,
  isRunning,
} from "../selectors"
import {ButtonWithIcon} from "../ButtonWithIcon"
import cn from "classnames"
import {RightPanel} from "../RightPanel"

type OwnPropsPick = Pick<PanelOwnProps,
  | "capabilities"
  | "isStateLoaded"
  | "processState"
  | "exportGraph">

type OwnProps = OwnPropsPick
type Props = OwnProps & StateProps

function ProcessPanel(props: Props) {
  const {
    capabilities, exportGraph, processId, deployPossible, saveDisabled, featuresSettings,
    processToDisplay, businessView, versionId, canExport,
    isRunning,
    hasOneVersion,
    exportProcessToJSON, exportProcessToPdf, importFiles, reportEvent, toggleConfirmDialog, toggleModalDialog,
  } = props

  const archiveProcess = () => !isRunning && toggleConfirmDialog(
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
  const exportToJSON = () => exportProcessToJSON(processToDisplay, versionId)
  const exportToPdf = () => exportProcessToPdf(processId, versionId, exportGraph(), businessView)

  const buttons = [
    {
      name: `save${!saveDisabled ? "*" : ""}`,
      onClick: save,
      icon: InlinedSvgs.buttonSave,
      disabled: saveDisabled,
      isHidden: !capabilities.write,
    },
    {
      name: "migrate",
      onClick: migrate,
      icon: InlinedSvgs.buttonMigrate,
      disabled: !deployPossible,
      isHidden: !capabilities.deploy || isEmpty(featuresSettings?.remoteEnvironment),
    },
    {
      name: "compare",
      onClick: compareVersions,
      icon: "compare.svg",
      disabled: hasOneVersion,
    },
    {
      name: "import",
      onClick: () => reportEvent({
        category: events.categories.rightPanel,
        action: events.actions.buttonClick,
        name: "import",
      }),
      icon: InlinedSvgs.buttonImport,
      disabled: false,
      isHidden: !capabilities.write,
      onDrop: importFromFiles,
    },
    {
      name: "JSON",
      onClick: exportToJSON,
      icon: InlinedSvgs.buttonExport,
      disabled: !canExport,
    },
    {
      name: "PDF",
      onClick: exportToPdf,
      icon: InlinedSvgs.pdf,
      disabled: !canExport,
    },
    {
      name: "archive",
      onClick: archiveProcess,
      icon: "archive.svg",
      disabled: isRunning,
      isHidden: !capabilities.write,
    },
  ]

  const panelName = "Process"
  return (
    <RightPanel title={panelName}>
      {buttons.map(({name, isHidden, ...props}) => isHidden ? null : (
        <ButtonWithIcon
          {...props}
          key={name}
          name={name}
          title={name}
          className={cn("espButton", "right-panel")}
        />
      ))}
    </RightPanel>
  )
}

const mapState = (state: RootState, props: OwnProps) => {
  return {
    processId: getProcessId(state),
    versionId: getProcessVersionId(state),
    processToDisplay: getProcessToDisplay(state),
    canExport: ProcessUtils.canExport(state),
    featuresSettings: getFeatureSettings(state),
    businessView: isBusinessView(state),
    saveDisabled: isSaveDisabled(state),
    hasErrors: hasError(state),
    deployPossible: isDeployPossible(state, props),
    isRunning: isRunning(state, props),
    hasOneVersion: hasOneVersion(state),
  }
}

const mapDispatch = (dispatch) => bindActionCreators({
  exportProcessToJSON,
  exportProcessToPdf,
  importFiles,
  reportEvent,
  toggleConfirmDialog,
  toggleModalDialog,
}, dispatch)

type StateProps = ReturnType<typeof mapDispatch> & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(ProcessPanel)
