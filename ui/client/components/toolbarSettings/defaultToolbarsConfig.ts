/* eslint-disable i18next/no-literal-string */
import {ToolbarsSide} from "../../reducers/toolbars"
import {WithId} from "../../types/common"
import {ButtonsVariant} from "../toolbarComponents/ToolbarButtons"
import {BuiltinButtonTypes} from "./buttons"
import {ToolbarsConfig} from "./types"

export const defaultToolbarsConfig: WithId<ToolbarsConfig> = {
  id: "random-string",
  [ToolbarsSide.TopRight]: [
    {
      id: "PROCESS-INFO",
      buttons: [
        {type: BuiltinButtonTypes.processSave},
        {type: BuiltinButtonTypes.deploy},
        {type: BuiltinButtonTypes.deployCanel},
        {type: BuiltinButtonTypes.deployMetrics},
        // {
        //   type: CustomButtonTypes.customLink,
        //   name: "metrics",
        //   icon: "toolbarButtons/metrics.svg",
        //   href: "/metrics/{{processId}}",
        // },
      ],
    },
    {
      id: "VIEW-PANEL",
      title: "view",
      buttons: [
        {type: BuiltinButtonTypes.viewBussinesView},
        {type: BuiltinButtonTypes.viewZoomIn},
        {type: BuiltinButtonTypes.viewZoomOut},
        {type: BuiltinButtonTypes.viewReset},
      ],
    },
    {
      id: "EDIT-PANEL",
      title: "edit",
      buttonsVariant: ButtonsVariant.small,
      buttons: [
        {type: BuiltinButtonTypes.editUndo},
        {type: BuiltinButtonTypes.editRedo},
        {type: BuiltinButtonTypes.editCopy},
        {type: BuiltinButtonTypes.editPaste},
        {type: BuiltinButtonTypes.editDelete},
        {type: BuiltinButtonTypes.editLayout},
      ],
    },
    {
      id: "PROCESS-PANELS",
      title: "process",
      buttons: [
        {type: BuiltinButtonTypes.editProperties},
        {type: BuiltinButtonTypes.processCompare},
        {type: BuiltinButtonTypes.processMigrate},
        {type: BuiltinButtonTypes.processImport},
        {type: BuiltinButtonTypes.processJSON},
        {type: BuiltinButtonTypes.processPDF},
        {type: BuiltinButtonTypes.processArchive},
      ],
    },
    {
      id: "TEST-PANEL",
      title: "test",
      buttons: [
        {type: BuiltinButtonTypes.testFromFile},
        {type: BuiltinButtonTypes.testGenerate},
        {type: BuiltinButtonTypes.testCounts},
        {type: BuiltinButtonTypes.testHide},
      ],
    },
    {
      id: "GROUP-PANEL",
      title: "group",
      buttons: [
        {type: BuiltinButtonTypes.group},
        {type: BuiltinButtonTypes.ungroup},
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
