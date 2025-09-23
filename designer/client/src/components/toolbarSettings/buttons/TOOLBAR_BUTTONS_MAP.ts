import loadable from "@loadable/component";
import type { ComponentType } from "react";

import { BuiltinButtonTypes } from "./BuiltinButtonTypes";
import { CustomButtonTypes } from "./CustomButtonTypes";
import type { ToolbarButton, ToolbarButtonTypes } from "./types";

export type PropsOfButton<T> = ToolbarButton & {
    type: T;
};

type ToolbarButtonsMap = {
    [T in ToolbarButtonTypes]: ComponentType<PropsOfButton<T>>;
};

export const TOOLBAR_BUTTONS_MAP = {
    [BuiltinButtonTypes.processSave]: loadable(() => import("../../toolbars/process/buttons/SaveButton")),
    [BuiltinButtonTypes.processDeploy]: loadable(() => import("../../toolbars/scenarioActions/buttons/DeployButton")),
    [BuiltinButtonTypes.processRedeploy]: loadable(() => import("../../toolbars/scenarioActions/buttons/RedeployButton")),
    [BuiltinButtonTypes.processCancel]: loadable(() => import("../../toolbars/scenarioActions/buttons/CancelDeployButton")),
    [BuiltinButtonTypes.processRunOffSchedule]: loadable(() => import("../../toolbars/scenarioActions/buttons/RunOffScheduleButton")),
    [BuiltinButtonTypes.viewZoomIn]: loadable(() => import("../../toolbars/view/buttons/ZoomInButton")),
    [BuiltinButtonTypes.viewZoomOut]: loadable(() => import("../../toolbars/view/buttons/ZoomOutButton")),
    [BuiltinButtonTypes.viewReset]: loadable(() => import("../../toolbars/view/buttons/ResetViewButton")),
    [BuiltinButtonTypes.editUndo]: loadable(() => import("../../toolbars/edit/buttons/UndoButton")),
    [BuiltinButtonTypes.editRedo]: loadable(() => import("../../toolbars/edit/buttons/RedoButton")),
    [BuiltinButtonTypes.editCopy]: loadable(() => import("../../toolbars/edit/buttons/CopyButton")),
    [BuiltinButtonTypes.editPaste]: loadable(() => import("../../toolbars/edit/buttons/PasteButton")),
    [BuiltinButtonTypes.editDelete]: loadable(() => import("../../toolbars/edit/buttons/DeleteButton")),
    [BuiltinButtonTypes.editLayout]: loadable(() => import("../../toolbars/edit/buttons/LayoutButton")),
    [BuiltinButtonTypes.processProperties]: loadable(() => import("../../toolbars/scenarioActions/buttons/PropertiesButton")),
    [BuiltinButtonTypes.processCompare]: loadable(() => import("../../toolbars/process/buttons/CompareButton")),
    [BuiltinButtonTypes.processMigrate]: loadable(() => import("../../toolbars/process/buttons/MigrateButton")),
    [BuiltinButtonTypes.processImport]: loadable(() => import("../../toolbars/process/buttons/ImportButton")),
    [BuiltinButtonTypes.processExport]: loadable(() => import("../../toolbars/process/buttons/ExportButton")),
    [BuiltinButtonTypes.processPDF]: loadable(() => import("../../toolbars/process/buttons/PDFButton")),
    [BuiltinButtonTypes.processArchiveToggle]: loadable(() => import("../../toolbars/process/buttons/ArchiveToggleButton")),
    [BuiltinButtonTypes.processArchive]: loadable(() => import("../../toolbars/process/buttons/ArchiveButton")),
    [BuiltinButtonTypes.processUnarchive]: loadable(() => import("../../toolbars/process/buttons/UnArchiveButton")),
    [BuiltinButtonTypes.testFromFile]: loadable(() => import("../../toolbars/test/buttons/FromFileButton")),
    [BuiltinButtonTypes.testGenerate]: loadable(() => import("../../toolbars/test/buttons/GenerateButton")),
    [BuiltinButtonTypes.testCounts]: loadable(() => import("../../toolbars/test/buttons/CountsButton")),
    [BuiltinButtonTypes.testHide]: loadable(() => import("../../toolbars/test/buttons/HideButton")),
    [CustomButtonTypes.customLink]: loadable(() => import("./LinkButton")),
    [CustomButtonTypes.adhocTesting]: loadable(() => import("../../toolbars/test/buttons/AdhocTestingButton")),
    [BuiltinButtonTypes.generateAndTest]: loadable(() => import("../../toolbars/test/buttons/GenerateAndTestButton")),
    [CustomButtonTypes.scenarioTest]: loadable(() => import("../../toolbars/test/buttons/ScenarioTestButton")),
    [BuiltinButtonTypes.liveData]: loadable(() => import("../../toolbars/test/buttons/LiveDataButton")),
} as ToolbarButtonsMap;
