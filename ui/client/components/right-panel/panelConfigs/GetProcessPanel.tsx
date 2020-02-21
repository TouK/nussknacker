/* eslint-disable i18next/no-literal-string */
import {PanelConfig} from "../PanelConfig"
import ProcessStateUtils from "../../Process/ProcessStateUtils"
import * as DialogMessages from "../../../common/DialogMessages"
import HttpService from "../../../http/HttpService"
import history from "../../../history"
import {Archive} from "../../../containers/Archive"
import {events} from "../../../analytics/TrackingEvents"
import Dialogs from "../../modals/Dialogs"
import InlinedSvgs from "../../../assets/icons/InlinedSvgs"
import {get, isEmpty} from "lodash"
import {OwnProps as PanelOwnProps} from "../UserRightPanel"
import {Props} from "../Panels1"

type A = Pick<PanelOwnProps & Props,
  | "canExport"
  | "capabilities"
  | "actions"
  | "featuresSettings"
  | "fetchedProcessDetails"
  | "exportGraph"
  | "businessView"
  | "processId"
  | "versionId"
  | "processToDisplay"
  | "fetchedProcessState"
  | "saveDisabled"
  | "deployPossible">

export function getProcessPanel(props: A): PanelConfig {
  const {
    canExport,
    capabilities,
    actions,
    featuresSettings,
    fetchedProcessDetails,
    exportGraph,
    businessView,
    processId,
    versionId,
    processToDisplay,
    fetchedProcessState,
    saveDisabled,
    deployPossible,
  } = props

  const isRunning = () => ProcessStateUtils.isRunning(fetchedProcessState)

  const archiveProcess = () => !isRunning() && actions.toggleConfirmDialog(
    true,
    DialogMessages.archiveProcess(processId),
    () => HttpService.archiveProcess(processId).then(() => history.push(Archive.path)),
    "Yes",
    "No",
    {category: events.categories.rightPanel, action: events.actions.buttonClick, name: "archive"},
  )

  const migrate = () => actions.toggleConfirmDialog(
    true,
    DialogMessages.migrate(processId, featuresSettings.remoteEnvironment.targetEnvironmentId),
    () => HttpService.migrateProcess(processId, versionId),
    "Yes",
    "No",
    {category: events.categories.rightPanel, action: events.actions.buttonClick, name: "migrate"},
  )

  const importFiles = (files) => actions.importFiles(files, processId)

  //TODO: Checking permission to archiwization should be done by check action from state - we should add new action type
  const compareVersions = () => actions.toggleModalDialog(Dialogs.types.compareVersions)
  const save = () => actions.toggleModalDialog(Dialogs.types.saveProcess)
  const hasOneVersion = () => get(fetchedProcessDetails, "history", []).length <= 1
  const exportProcess = () => actions.exportProcessToJSON(processToDisplay, versionId)
  const exportProcessToPdf = () => actions.exportProcessToPdf(processId, versionId, exportGraph(), businessView)

  return {
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
      {name: "compare", onClick: compareVersions, icon: "compare.svg", disabled: hasOneVersion()},
      {
        name: "import",
        isHidden: !capabilities.write,
        disabled: false,
        onClick: () => actions.reportEvent({
          category: events.categories.rightPanel,
          action: events.actions.buttonClick,
          name: "import",
        }),
        onDrop: importFiles,
        icon: InlinedSvgs.buttonImport,
      },
      {name: "JSON", disabled: !canExport, onClick: exportProcess, icon: InlinedSvgs.buttonExport},
      {name: "PDF", disabled: !canExport, onClick: exportProcessToPdf, icon: InlinedSvgs.pdf},
      {
        name: "archive",
        onClick: archiveProcess,
        disabled: isRunning(),
        icon: "archive.svg",
        isHidden: !capabilities.write,
      },
    ],
  }

}
