import {ToolbarsSide} from "../../reducers/toolbars"
import {ButtonsVariant} from "../toolbarComponents/ToolbarButtons"
import {BuiltinButtonTypes, ToolbarButtonTypes} from "./buttons"

export interface ToolbarConfig {
  id: string,
  buttons?: ToolbarButtonTypes[],
  buttonsVariant?: ButtonsVariant,
}

export type ToolbarsConfig = Partial<Record<ToolbarsSide, ToolbarConfig[]>>

export const defaultToolbarsConfig: ToolbarsConfig = {
  [ToolbarsSide.TopRight]: [
    {
      id: "PROCESS-INFO",
      buttons: [
        BuiltinButtonTypes.processSave,
        BuiltinButtonTypes.deploy,
        BuiltinButtonTypes.deployCanel,
        BuiltinButtonTypes.deployMetrics,
      ],
    },
    {
      id: "VIEW-PANEL",
      buttons: [
        BuiltinButtonTypes.viewBussinesView,
        BuiltinButtonTypes.viewZoomIn,
        BuiltinButtonTypes.viewZoomOut,
        BuiltinButtonTypes.viewReset,
      ],
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
    },
    {
      id: "TEST-PANEL",
      buttons: [
        BuiltinButtonTypes.testFromFile,
        BuiltinButtonTypes.testGenerate,
        BuiltinButtonTypes.testCounts,
        BuiltinButtonTypes.testHide,
      ],
    },
    {
      id: "GROUP-PANEL",
      buttons: [
        BuiltinButtonTypes.groupStart,
        BuiltinButtonTypes.groupFinish,
        BuiltinButtonTypes.groupCancel,
        BuiltinButtonTypes.groupUngroup,
      ],
    },
    {id: "DETAILS-PANEL"},
  ],
  [ToolbarsSide.TopLeft]: [
    {id: "TIPS-PANEL"},
    {id: "CREATOR-PANEL"},
    {id: "VERSIONS-PANEL"},
    {id: "COMMENTS-PANEL"},
    {id: "ATTACHMENTS-PANEL"},
  ],
}
