import {ComponentType} from "react"
import CopyButton from "../edit/buttons/CopyButton"
import DeleteButton from "../edit/buttons/DeleteButton"
import LayoutButton from "../edit/buttons/LayoutButton"
import PasteButton from "../edit/buttons/PasteButton"
import RedoButton from "../edit/buttons/RedoButton"
import UndoButton from "../edit/buttons/UndoButton"
import GroupCancelButton from "../group/buttons/GroupCancelButton"
import GroupFinishButton from "../group/buttons/GroupFinishButton"
import GroupStartButton from "../group/buttons/GroupStartButton"
import UngroupButton from "../group/buttons/UngroupButton"
import ArchiveButton from "../process/buttons/ArchiveButton"
import {ArchiveToggleButton} from "../process/buttons/ArchiveToggleButton"
import CompareButton from "../process/buttons/CompareButton"
import ImportButton from "../process/buttons/ImportButton"
import JSONButton from "../process/buttons/JSONButton"
import MigrateButton from "../process/buttons/MigrateButton"
import PDFButton from "../process/buttons/PDFButton"
import SaveButton from "../process/buttons/SaveButton"
import UnArchiveButton from "../process/buttons/UnArchiveButton"
import {ActionButton} from "../status/buttons/ActionButton"
import CancelDeployButton from "../status/buttons/CancelDeployButton"
import DeployButton from "../status/buttons/DeployButton"
import MetricsButton from "../status/buttons/MetricsButton"
import PropertiesButton from "../status/buttons/PropertiesButton"
import CountsButton from "../test/buttons/CountsButton"
import FromFileButton from "../test/buttons/FromFileButton"
import GenerateButton from "../test/buttons/GenerateButton"
import HideButton from "../test/buttons/HideButton"
import BussinesViewSwitch from "../view/buttons/BussinesViewSwitch"
import {ResetViewButton} from "../view/buttons/ResetViewButton"
import {ZoomInButton} from "../view/buttons/ZoomInButton"
import {ZoomOutButton} from "../view/buttons/ZoomOutButton"
import {BuiltinButtonTypes} from "./BuiltinButtonTypes"
import {CustomButtonTypes} from "./CustomButtonTypes"
import {ToolbarButtonSettings, ToolbarButtonTypes} from "./ToolbarSettingsTypes"

export const TOOLBAR_BUTTONS_MAP: Record<ToolbarButtonTypes, ComponentType<ToolbarButtonSettings>> = {
  [BuiltinButtonTypes.processSave]: SaveButton,
  [BuiltinButtonTypes.deploy]: DeployButton,
  [BuiltinButtonTypes.deployCanel]: CancelDeployButton,
  [BuiltinButtonTypes.deployMetrics]: MetricsButton, // like "custom-link" but disabled for subprocess
  [BuiltinButtonTypes.viewBussinesView]: BussinesViewSwitch,
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
  [BuiltinButtonTypes.groupStart]: GroupStartButton,
  [BuiltinButtonTypes.groupFinish]: GroupFinishButton,
  [BuiltinButtonTypes.groupCancel]: GroupCancelButton,
  [BuiltinButtonTypes.groupUngroup]: UngroupButton,
  [CustomButtonTypes.customAction]: ActionButton,
}
