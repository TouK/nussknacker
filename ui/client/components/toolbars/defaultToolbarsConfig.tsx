import {ToolbarsSide} from "../../reducers/toolbars"
import {ButtonsVariant} from "../toolbarComponents/ToolbarButtons"
import {BuiltinButtonTypes} from "./toolbarSettings/BuiltinButtonTypes"
import {ToolbarButtonTypes} from "./toolbarSettings/ToolbarSettingsTypes"

type ToolbarConfig = {
  id: string,
  defaultSide: ToolbarsSide,
  buttons?: ToolbarButtonTypes[],
  buttonsVariant?: ButtonsVariant,
}

export const defaultToolbarsConfig: ToolbarConfig[] = [
  {
    id: "PROCESS-INFO",
    buttons: [
      BuiltinButtonTypes.processSave,
      BuiltinButtonTypes.deploy,
      BuiltinButtonTypes.deployCanel,
      BuiltinButtonTypes.deployMetrics,
    ],
    defaultSide: ToolbarsSide.TopRight,
  },
  {
    id: "VIEW-PANEL",
    buttons: [
      BuiltinButtonTypes.viewBussinesView,
      BuiltinButtonTypes.viewZoomIn,
      BuiltinButtonTypes.viewZoomOut,
      BuiltinButtonTypes.viewReset,
    ],
    defaultSide: ToolbarsSide.TopRight,
  },
  {
    id: "EDIT-PANEL",
    buttonsVariant: ButtonsVariant.small,
    buttons: [
      BuiltinButtonTypes.editUndo,
      BuiltinButtonTypes.editRedo,
      BuiltinButtonTypes.editCopy,
      BuiltinButtonTypes.editPaste,
      BuiltinButtonTypes.editDelete,
      BuiltinButtonTypes.editLayout,
    ],
    defaultSide: ToolbarsSide.TopRight,
  },
  {
    id: "PROCESS-PANELS",
    buttons: [
      BuiltinButtonTypes.editProperties,
      BuiltinButtonTypes.processCompare,
      BuiltinButtonTypes.processMigrate,
      BuiltinButtonTypes.processImport,
      BuiltinButtonTypes.processJSON,
      BuiltinButtonTypes.processPDF,
      BuiltinButtonTypes.processArchive,
    ],
    defaultSide: ToolbarsSide.TopRight,
  },
  {
    id: "TEST-PANEL",
    buttons: [
      BuiltinButtonTypes.testFromFile,
      BuiltinButtonTypes.testGenerate,
      BuiltinButtonTypes.testCounts,
      BuiltinButtonTypes.testHide,
    ],
    defaultSide: ToolbarsSide.TopRight,
  },
  {
    id: "GROUP-PANEL",
    buttons: [
      BuiltinButtonTypes.groupStart,
      BuiltinButtonTypes.groupFinish,
      BuiltinButtonTypes.groupCancel,
      BuiltinButtonTypes.groupUngroup,
    ],
    defaultSide: ToolbarsSide.TopRight,
  },
  {
    id: "DETAILS-PANEL",
    defaultSide: ToolbarsSide.TopRight,
  },
  {
    id: "TIPS-PANEL",
    defaultSide: ToolbarsSide.TopLeft,
  },
  {
    id: "CREATOR-PANEL",
    defaultSide: ToolbarsSide.TopLeft,
  },
  {
    id: "VERSIONS-PANEL",
    defaultSide: ToolbarsSide.TopLeft,
  },
  {
    id: "COMMENTS-PANEL",
    defaultSide: ToolbarsSide.TopLeft,
  },
  {
    id: "ATTACHMENTS-PANEL",
    defaultSide: ToolbarsSide.TopLeft,
  },
]
