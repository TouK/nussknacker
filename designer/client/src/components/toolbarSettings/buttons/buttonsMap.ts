import type { ComponentType } from "react";

import CopyButton from "../../toolbars/edit/buttons/CopyButton";
import DeleteButton from "../../toolbars/edit/buttons/DeleteButton";
import LayoutButton from "../../toolbars/edit/buttons/LayoutButton";
import PasteButton from "../../toolbars/edit/buttons/PasteButton";
import RedoButton from "../../toolbars/edit/buttons/RedoButton";
import UndoButton from "../../toolbars/edit/buttons/UndoButton";
import ArchiveButton from "../../toolbars/process/buttons/ArchiveButton";
import ArchiveToggleButton from "../../toolbars/process/buttons/ArchiveToggleButton";
import CompareButton from "../../toolbars/process/buttons/CompareButton";
import ExportButton from "../../toolbars/process/buttons/ExportButton";
import ImportButton from "../../toolbars/process/buttons/ImportButton";
import MigrateButton from "../../toolbars/process/buttons/MigrateButton";
import PDFButton from "../../toolbars/process/buttons/PDFButton";
import SaveButton from "../../toolbars/process/buttons/SaveButton";
import UnArchiveButton from "../../toolbars/process/buttons/UnArchiveButton";
import CancelDeployButton from "../../toolbars/scenarioActions/buttons/CancelDeployButton";
import DeployButton from "../../toolbars/scenarioActions/buttons/DeployButton";
import PropertiesButton from "../../toolbars/scenarioActions/buttons/PropertiesButton";
import RedeployButton from "../../toolbars/scenarioActions/buttons/RedeployButton";
import RunOffScheduleButton from "../../toolbars/scenarioActions/buttons/RunOffScheduleButton";
import AdhocTestingButton from "../../toolbars/test/buttons/AdhocTestingButton";
import CountsButton from "../../toolbars/test/buttons/CountsButton";
import FromFileButton from "../../toolbars/test/buttons/FromFileButton";
import GenerateAndTestButton from "../../toolbars/test/buttons/GenerateAndTestButton";
import GenerateButton from "../../toolbars/test/buttons/GenerateButton";
import HideButton from "../../toolbars/test/buttons/HideButton";
import LiveDataButton from "../../toolbars/test/buttons/LiveDataButton";
import ScenarioTestButton from "../../toolbars/test/buttons/ScenarioTestButton";
import SnowSnowButton from "../../toolbars/test/buttons/SnowSnowButton";
import ResetViewButton from "../../toolbars/view/buttons/ResetViewButton";
import ZoomInButton from "../../toolbars/view/buttons/ZoomInButton";
import ZoomOutButton from "../../toolbars/view/buttons/ZoomOutButton";
import LinkButton from "./LinkButton";
import type { PropsOfButton, ToolbarButtonTypes } from "./types";

export enum BuiltinButtonTypes {
    processSave = "process-save",
    processDeploy = "process-deploy",
    processRedeploy = "process-redeploy",
    processCancel = "process-cancel",
    processRunOffSchedule = "process-run-off-schedule",
    editUndo = "edit-undo",
    editRedo = "edit-redo",
    editCopy = "edit-copy",
    editPaste = "edit-paste",
    editDelete = "edit-delete",
    editLayout = "edit-layout",
    alignHorizontalLeft = "align-horizontal-left",
    alignHorizontalCenter = "align-horizontal-center",
    alignHorizontalRight = "align-horizontal-right",
    alignVerticalTop = "align-vertical-top",
    alignVerticalCenter = "align-vertical-center",
    alignVerticalBottom = "align-vertical-bottom",
    distributeHorizontal = "distribute-horizontal",
    distributeVertical = "distribute-vertical",
    processProperties = "process-properties",
    processCompare = "process-compare",
    processMigrate = "process-migrate",
    processImport = "process-import",
    processExport = "process-export",
    processPDF = "process-pdf",
    processArchiveToggle = "process-archive-toggle",
    processArchive = "process-archive",
    processUnarchive = "process-unarchive",
    testFromFile = "test-from-file",
    testGenerate = "test-generate",
    generateAndTest = "generate-and-test",
    testCounts = "test-counts",
    testHide = "test-hide",
    liveData = "live-data",
    viewZoomIn = "view-zoom-in",
    viewZoomOut = "view-zoom-out",
    viewReset = "view-reset",
    snowSnow = "snow-snow",
}

export enum CustomButtonTypes {
    customLink = "custom-link",
    adhocTesting = "adhoc-testing",
    scenarioTest = "scenario-test",
}

type ToolbarButtonsMap = {
    [T in ToolbarButtonTypes]: ComponentType<PropsOfButton<T>>;
};

export const TOOLBAR_BUTTONS_MAP: ToolbarButtonsMap = {
    [BuiltinButtonTypes.processSave]: SaveButton,
    [BuiltinButtonTypes.processDeploy]: DeployButton,
    [BuiltinButtonTypes.processRedeploy]: RedeployButton,
    [BuiltinButtonTypes.processCancel]: CancelDeployButton,
    [BuiltinButtonTypes.processRunOffSchedule]: RunOffScheduleButton,
    [BuiltinButtonTypes.viewZoomIn]: ZoomInButton,
    [BuiltinButtonTypes.viewZoomOut]: ZoomOutButton,
    [BuiltinButtonTypes.viewReset]: ResetViewButton,
    [BuiltinButtonTypes.editUndo]: UndoButton,
    [BuiltinButtonTypes.editRedo]: RedoButton,
    [BuiltinButtonTypes.editCopy]: CopyButton,
    [BuiltinButtonTypes.editPaste]: PasteButton,
    [BuiltinButtonTypes.editDelete]: DeleteButton,
    [BuiltinButtonTypes.editLayout]: LayoutButton,
    [BuiltinButtonTypes.alignHorizontalLeft]: LayoutButton,
    [BuiltinButtonTypes.alignHorizontalCenter]: LayoutButton,
    [BuiltinButtonTypes.alignHorizontalRight]: LayoutButton,
    [BuiltinButtonTypes.alignVerticalTop]: LayoutButton,
    [BuiltinButtonTypes.alignVerticalCenter]: LayoutButton,
    [BuiltinButtonTypes.alignVerticalBottom]: LayoutButton,
    [BuiltinButtonTypes.distributeHorizontal]: LayoutButton,
    [BuiltinButtonTypes.distributeVertical]: LayoutButton,
    [BuiltinButtonTypes.processProperties]: PropertiesButton,
    [BuiltinButtonTypes.processCompare]: CompareButton,
    [BuiltinButtonTypes.processMigrate]: MigrateButton,
    [BuiltinButtonTypes.processImport]: ImportButton,
    [BuiltinButtonTypes.processExport]: ExportButton,
    [BuiltinButtonTypes.processPDF]: PDFButton,
    [BuiltinButtonTypes.processArchiveToggle]: ArchiveToggleButton,
    [BuiltinButtonTypes.processArchive]: ArchiveButton,
    [BuiltinButtonTypes.processUnarchive]: UnArchiveButton,
    [BuiltinButtonTypes.testFromFile]: FromFileButton,
    [BuiltinButtonTypes.testGenerate]: GenerateButton,
    [BuiltinButtonTypes.testCounts]: CountsButton,
    [BuiltinButtonTypes.testHide]: HideButton,
    [CustomButtonTypes.customLink]: LinkButton,
    [CustomButtonTypes.adhocTesting]: AdhocTestingButton,
    [BuiltinButtonTypes.generateAndTest]: GenerateAndTestButton,
    [CustomButtonTypes.scenarioTest]: ScenarioTestButton,
    [BuiltinButtonTypes.liveData]: LiveDataButton,
    [BuiltinButtonTypes.snowSnow]: SnowSnowButton,
};
