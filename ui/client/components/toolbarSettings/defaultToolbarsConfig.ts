/* eslint-disable i18next/no-literal-string */
import {ToolbarsSide} from "../../reducers/toolbars"
import {WithId} from "../../types/common"
import {ButtonsVariant} from "../toolbarComponents/ToolbarButtons"
import {BuiltinButtonTypes} from "./buttons"
import {ToolbarsConfig} from "./types"

export const DEV_TOOLBARS = process.env.NODE_ENV !== "production" ?
  [
    {id: "user-settings-panel"},
  ] :
  []

//It's only to local development
export function defaultToolbarsConfig(isSubprocess: boolean, isArchived: boolean): WithId<ToolbarsConfig> {

  const processPanelButtons = (!isSubprocess ? [{type: BuiltinButtonTypes.processProperties}] : []).concat(
    [
      {type: BuiltinButtonTypes.processCompare},
      {type: BuiltinButtonTypes.processMigrate},
      {type: BuiltinButtonTypes.processImport},
      {type: BuiltinButtonTypes.processJSON},
      {type: BuiltinButtonTypes.processPDF},
      {type: BuiltinButtonTypes.processArchiveToggle},
    ]
  )

  return {
    id: "a7334f9b-87aa-43d4-82ce-a9ac9dd3e5dc",
    [ToolbarsSide.TopRight]: [
      {
        id: "process-info-panel",
        buttons: [
          {type: BuiltinButtonTypes.processSave},
          {type: BuiltinButtonTypes.processDeploy},
          {type: BuiltinButtonTypes.processCancel},
        ],
      },
      {
        id: "view-panel",
        title: "view",
        buttons: [
          {type: BuiltinButtonTypes.viewBusinessView},
          {type: BuiltinButtonTypes.viewZoomIn},
          {type: BuiltinButtonTypes.viewZoomOut},
          {type: BuiltinButtonTypes.viewReset},
        ],
      },
      {
        id: "edit-panel",
        title: "edit",
        buttonsVariant: ButtonsVariant.small,
        buttons: isArchived ?
          [] :
          [
            {type: BuiltinButtonTypes.editUndo},
            {type: BuiltinButtonTypes.editRedo},
            {type: BuiltinButtonTypes.editCopy},
            {type: BuiltinButtonTypes.editPaste},
            {type: BuiltinButtonTypes.editDelete},
            {type: BuiltinButtonTypes.editLayout},
          ],
      },
      {
        id: "process-panel",
        title: "process",
        buttons: processPanelButtons,
      },
      {
        id: "test-panel",
        title: "test",
        buttons: [
          {type: BuiltinButtonTypes.testFromFile},
          {type: BuiltinButtonTypes.testGenerate},
          {type: BuiltinButtonTypes.testCounts},
          {type: BuiltinButtonTypes.testHide},
        ],
      },
      {
        id: "group-panel",
        title: "group",
        buttons: isArchived ?
          [] :
          [
            {type: BuiltinButtonTypes.group},
            {type: BuiltinButtonTypes.ungroup},
          ],
      },
      {id: "details-panel"},
    ],
    [ToolbarsSide.TopLeft]: [
      {id: "tips-panel"},
      {id: "creator-panel"},
      {id: "versions-panel"},
      {id: "comments-panel"},
      {id: "attachments-panel"},
    ],
    [ToolbarsSide.BottomRight]: DEV_TOOLBARS,
  }
}
