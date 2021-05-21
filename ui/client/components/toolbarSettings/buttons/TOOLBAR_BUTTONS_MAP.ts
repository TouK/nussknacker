import {ComponentType} from "react"
import CopyButton from "../../toolbars/edit/buttons/CopyButton"
import DeleteButton from "../../toolbars/edit/buttons/DeleteButton"
import LayoutButton from "../../toolbars/edit/buttons/LayoutButton"
import PasteButton from "../../toolbars/edit/buttons/PasteButton"
import RedoButton from "../../toolbars/edit/buttons/RedoButton"
import UndoButton from "../../toolbars/edit/buttons/UndoButton"
import {GroupButton} from "../../toolbars/group/buttons/GroupButton"
import {UngroupButton} from "../../toolbars/group/buttons/UngroupButton"
import ArchiveButton from "../../toolbars/process/buttons/ArchiveButton"
import {ArchiveToggleButton} from "../../toolbars/process/buttons/ArchiveToggleButton"
import CompareButton from "../../toolbars/process/buttons/CompareButton"
import ImportButton from "../../toolbars/process/buttons/ImportButton"
import JSONButton from "../../toolbars/process/buttons/JSONButton"
import MigrateButton from "../../toolbars/process/buttons/MigrateButton"
import PDFButton from "../../toolbars/process/buttons/PDFButton"
import SaveButton from "../../toolbars/process/buttons/SaveButton"
import UnArchiveButton from "../../toolbars/process/buttons/UnArchiveButton"
import {LinkButton} from "./LinkButton"
import {ActionButton} from "./ActionButton"
import CancelDeployButton from "../../toolbars/status/buttons/CancelDeployButton"
import DeployButton from "../../toolbars/status/buttons/DeployButton"
import MetricsButton from "../../toolbars/status/buttons/MetricsButton"
import PropertiesButton from "../../toolbars/status/buttons/PropertiesButton"
import CountsButton from "../../toolbars/test/buttons/CountsButton"
import FromFileButton from "../../toolbars/test/buttons/FromFileButton"
import GenerateButton from "../../toolbars/test/buttons/GenerateButton"
import HideButton from "../../toolbars/test/buttons/HideButton"
import BussinesViewSwitch from "../../toolbars/view/buttons/BussinesViewSwitch"
import {ResetViewButton} from "../../toolbars/view/buttons/ResetViewButton"
import {ZoomInButton} from "../../toolbars/view/buttons/ZoomInButton"
import {ZoomOutButton} from "../../toolbars/view/buttons/ZoomOutButton"
import {BuiltinButtonTypes} from "./BuiltinButtonTypes"
import {CustomButtonTypes} from "./CustomButtonTypes"
import {ToolbarButton, ToolbarButtonTypes} from "./types"

type ToolbarButtonsMap = {
  [T in ToolbarButtonTypes]: ComponentType<ToolbarButton & {type: T}>
}

export const TOOLBAR_BUTTONS_MAP: ToolbarButtonsMap = {
  [BuiltinButtonTypes.processSave]: SaveButton,
  [BuiltinButtonTypes.deploy]: DeployButton,
  [BuiltinButtonTypes.deployCancel]: CancelDeployButton,
  [BuiltinButtonTypes.deployMetrics]: MetricsButton, // like "custom-link" but disabled for subprocess
  [BuiltinButtonTypes.viewBusinessView]: BussinesViewSwitch,
  [BuiltinButtonTypes.viewZoomIn]: ZoomInButton,
  [BuiltinButtonTypes.viewZoomOut]: ZoomOutButton,
  [BuiltinButtonTypes.viewReset]: ResetViewButton,
  [BuiltinButtonTypes.editUndo]: UndoButton,
  [BuiltinButtonTypes.editRedo]: RedoButton,
  [BuiltinButtonTypes.editCopy]: CopyButton,
  [BuiltinButtonTypes.editPaste]: PasteButton,
  [BuiltinButtonTypes.editDelete]: DeleteButton,
  [BuiltinButtonTypes.editLayout]: LayoutButton,
  [BuiltinButtonTypes.editProperties]: PropertiesButton,
  [BuiltinButtonTypes.processCompare]: CompareButton,
  [BuiltinButtonTypes.processMigrate]: MigrateButton,
  [BuiltinButtonTypes.processImport]: ImportButton,
  [BuiltinButtonTypes.processJSON]: JSONButton,
  [BuiltinButtonTypes.processPDF]: PDFButton,
  [BuiltinButtonTypes.processArchiveToggle]: ArchiveToggleButton,
  [BuiltinButtonTypes.processArchive]: ArchiveButton,
  [BuiltinButtonTypes.processUnarchive]: UnArchiveButton,
  [BuiltinButtonTypes.testFromFile]: FromFileButton,
  [BuiltinButtonTypes.testGenerate]: GenerateButton,
  [BuiltinButtonTypes.testCounts]: CountsButton,
  [BuiltinButtonTypes.testHide]: HideButton,
  [BuiltinButtonTypes.group]: GroupButton,
  [BuiltinButtonTypes.ungroup]: UngroupButton,
  [CustomButtonTypes.customAction]: ActionButton,
  [CustomButtonTypes.customLink]: LinkButton,
}
