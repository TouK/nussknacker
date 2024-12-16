import { ComponentType } from "react";
import CopyButton from "../../toolbars/edit/buttons/CopyButton";
import DeleteButton from "../../toolbars/edit/buttons/DeleteButton";
import LayoutButton from "../../toolbars/edit/buttons/LayoutButton";
import PasteButton from "../../toolbars/edit/buttons/PasteButton";
import RedoButton from "../../toolbars/edit/buttons/RedoButton";
import UndoButton from "../../toolbars/edit/buttons/UndoButton";
import ArchiveButton from "../../toolbars/process/buttons/ArchiveButton";
import { ArchiveToggleButton } from "../../toolbars/process/buttons/ArchiveToggleButton";
import CompareButton from "../../toolbars/process/buttons/CompareButton";
import ImportButton from "../../toolbars/process/buttons/ImportButton";
import JSONButton from "../../toolbars/process/buttons/JSONButton";
import MigrateButton from "../../toolbars/process/buttons/MigrateButton";
import PDFButton from "../../toolbars/process/buttons/PDFButton";
import SaveButton from "../../toolbars/process/buttons/SaveButton";
import UnArchiveButton from "../../toolbars/process/buttons/UnArchiveButton";
import { LinkButton } from "./LinkButton";
import { ActionButton } from "./ActionButton";
import CancelDeployButton from "../../toolbars/scenarioActions/buttons/CancelDeployButton";
import DeployButton from "../../toolbars/scenarioActions/buttons/DeployButton";
import PropertiesButton from "../../toolbars/scenarioActions/buttons/PropertiesButton";
import CountsButton from "../../toolbars/test/buttons/CountsButton";
import FromFileButton from "../../toolbars/test/buttons/FromFileButton";
import GenerateButton from "../../toolbars/test/buttons/GenerateButton";
import GenerateAndTestButton from "../../toolbars/test/buttons/GenerateAndTestButton";
import AdhocTestingButton from "../../toolbars/test/buttons/AdhocTestingButton";
import HideButton from "../../toolbars/test/buttons/HideButton";
import { ResetViewButton } from "../../toolbars/view/buttons/ResetViewButton";
import { ZoomInButton } from "../../toolbars/view/buttons/ZoomInButton";
import { ZoomOutButton } from "../../toolbars/view/buttons/ZoomOutButton";
import { BuiltinButtonTypes } from "./BuiltinButtonTypes";
import { CustomButtonTypes } from "./CustomButtonTypes";
import { ToolbarButton, ToolbarButtonTypes } from "./types";
import RunOutOfScheduleButton from "../../toolbars/scenarioActions/buttons/RunOutOfScheduleButton";

export type PropsOfButton<T> = ToolbarButton & {
    type: T;
};

type ToolbarButtonsMap = {
    [T in ToolbarButtonTypes]: ComponentType<PropsOfButton<T>>;
};

export const TOOLBAR_BUTTONS_MAP: ToolbarButtonsMap = {
    [BuiltinButtonTypes.processSave]: SaveButton,
    [BuiltinButtonTypes.processDeploy]: DeployButton,
    [BuiltinButtonTypes.processCancel]: CancelDeployButton,
    [BuiltinButtonTypes.processRunOutOfSchedule]: RunOutOfScheduleButton,
    [BuiltinButtonTypes.viewZoomIn]: ZoomInButton,
    [BuiltinButtonTypes.viewZoomOut]: ZoomOutButton,
    [BuiltinButtonTypes.viewReset]: ResetViewButton,
    [BuiltinButtonTypes.editUndo]: UndoButton,
    [BuiltinButtonTypes.editRedo]: RedoButton,
    [BuiltinButtonTypes.editCopy]: CopyButton,
    [BuiltinButtonTypes.editPaste]: PasteButton,
    [BuiltinButtonTypes.editDelete]: DeleteButton,
    [BuiltinButtonTypes.editLayout]: LayoutButton,
    [BuiltinButtonTypes.processProperties]: PropertiesButton,
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
    [CustomButtonTypes.customAction]: ActionButton,
    [CustomButtonTypes.customLink]: LinkButton,
    [CustomButtonTypes.adhocTesting]: AdhocTestingButton,
    [BuiltinButtonTypes.generateAndTest]: GenerateAndTestButton,
};
