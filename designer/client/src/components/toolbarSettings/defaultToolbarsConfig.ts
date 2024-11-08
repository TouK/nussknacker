/* eslint-disable i18next/no-literal-string */
import { ToolbarsSide } from "../../reducers/toolbars";
import { WithId } from "../../types/common";
import { ButtonsVariant } from "../toolbarComponents/toolbarButtons";
import { BuiltinButtonTypes } from "./buttons";
import { DEV_TOOLBARS } from "./DEV_TOOLBARS";
import { ToolbarsConfig } from "./types";

//It's only to local development
export function defaultToolbarsConfig(isFragment: boolean, isArchived: boolean): WithId<ToolbarsConfig> {
    const processPanelButtons = (!isFragment ? [{ type: BuiltinButtonTypes.processProperties }] : []).concat([
        { type: BuiltinButtonTypes.processCompare },
        { type: BuiltinButtonTypes.processMigrate },
        { type: BuiltinButtonTypes.processImport },
        { type: BuiltinButtonTypes.processJSON },
        { type: BuiltinButtonTypes.processPDF },
        { type: BuiltinButtonTypes.processArchiveToggle },
    ]);

    return {
        id: "a7334f9b-87aa-43d4-82ce-a9ac9dd3e5dc",
        [ToolbarsSide.TopRight]: [
            {
                id: "process-info-panel",
            },
            {
                id: "process-actions-panel",
                buttons: [
                    { type: BuiltinButtonTypes.processSave },
                    { type: BuiltinButtonTypes.processDeploy },
                    { type: BuiltinButtonTypes.processCancel },
                    { type: BuiltinButtonTypes.processRunOffSchedule },
                ],
            },
            {
                id: "view-panel",
                title: "view",
                buttons: [
                    { type: BuiltinButtonTypes.viewZoomIn },
                    { type: BuiltinButtonTypes.viewZoomOut },
                    { type: BuiltinButtonTypes.viewReset },
                ],
            },
            {
                id: "edit-panel",
                title: "edit",
                buttonsVariant: ButtonsVariant.small,
                buttons: isArchived
                    ? []
                    : [
                          { type: BuiltinButtonTypes.editUndo },
                          { type: BuiltinButtonTypes.editRedo },
                          { type: BuiltinButtonTypes.editCopy },
                          { type: BuiltinButtonTypes.editPaste },
                          { type: BuiltinButtonTypes.editDelete },
                          { type: BuiltinButtonTypes.editLayout },
                      ],
            },
            {
                id: "process-panel",
                title: "scenario",
                buttons: processPanelButtons,
            },
            {
                id: "test-panel",
                title: "test",
                buttons: [
                    { type: BuiltinButtonTypes.testFromFile },
                    { type: BuiltinButtonTypes.testGenerate },
                    { type: BuiltinButtonTypes.testCounts },
                    { type: BuiltinButtonTypes.testHide },
                ],
            },
        ],
        [ToolbarsSide.TopLeft]: [
            { id: "survey-panel" },
            { id: "tips-panel" },
            { id: "sticky-notes-panel" },
            { id: "creator-panel" },
            { id: "activities-panel" },
        ],
        [ToolbarsSide.BottomRight]: DEV_TOOLBARS,
    };
}
