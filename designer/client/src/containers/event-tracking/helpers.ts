import { TrackEventParams } from "./use-event-tracking";
import { BuiltinButtonTypes, CustomButtonTypes } from "../../components/toolbarSettings/buttons";

export const removeEventSelectors = () => {
    const body = document.querySelector("body");
    delete body.dataset.statisticEvent;
};

const eventSelectorName = "data-statistic-event";

export const getEventTrackingProps = ({ type }: TrackEventParams) => {
    return { [eventSelectorName]: type };
};
export enum EventTrackingType {
    SearchScenariosByName = "SEARCH_SCENARIOS_BY_NAME",
    FilterScenariosByStatus = "FILTER_SCENARIOS_BY_STATUS",
    FilterScenariosByProcessingMode = "FILTER_SCENARIOS_BY_PROCESSING_MODE",
    FilterScenariosByCategory = "FILTER_SCENARIOS_BY_CATEGORY",
    FilterScenariosByAuthor = "FILTER_SCENARIOS_BY_AUTHOR",
    FilterScenariosByOther = "FILTER_SCENARIOS_BY_OTHER",
    SortScenarios = "SORT_SCENARIOS",
    SearchComponentsByName = "SEARCH_COMPONENTS_BY_NAME",
    FilterComponentsByGroup = "FILTER_COMPONENTS_BY_GROUP",
    FilterComponentsByProcessingMode = "FILTER_COMPONENTS_BY_PROCESSING_MODE",
    FilterComponentsByCategory = "FILTER_COMPONENTS_BY_CATEGORY",
    FilterComponentsByMultipleCategories = "FILTER_COMPONENTS_BY_MULTIPLE_CATEGORIES",
    FilterComponentsByUsages = "FILTER_COMPONENTS_BY_USAGES",
    ClickComponentUsages = "CLICK_COMPONENT_USAGES",
    SearchComponentUsagesByName = "SEARCH_COMPONENT_USAGES_BY_NAME",
    FilterComponentUsagesByStatus = "FILTER_COMPONENT_USAGES_BY_STATUS",
    FilterComponentUsagesByCategory = "FILTER_COMPONENT_USAGES_BY_CATEGORY",
    FilterComponentUsagesByAuthor = "FILTER_COMPONENT_USAGES_BY_AUTHOR",
    FilterComponentUsagesByOther = "FILTER_COMPONENT_USAGES_BY_OTHER",
    ClickScenarioFromComponentUsages = "CLICK_SCENARIO_FROM_COMPONENT_USAGES",
    ClickGlobalMetricsTab = "CLICK_GLOBAL_METRICS_TAB",
    ClickActionDeploy = "CLICK_ACTION_DEPLOY",
    ClickActionMetrics = "CLICK_ACTION_METRICS",
    ClickViewZoomIn = "CLICK_VIEW_ZOOM_IN",
    ClickViewZoomOut = "CLICK_VIEW_ZOOM_OUT",
    ClickViewReset = "CLICK_VIEW_RESET",
    ClickEditUndo = "CLICK_EDIT_UNDO",
    ClickEditRedo = "CLICK_EDIT_REDO",
    ClickEditCopy = "CLICK_EDIT_COPY",
    ClickEditPaste = "CLICK_EDIT_PASTE",
    ClickEditDelete = "CLICK_EDIT_DELETE",
    ClickEditLayout = "CLICK_EDIT_LAYOUT",
    ClickScenarioProperties = "CLICK_SCENARIO_PROPERTIES",
    ClickScenarioCompare = "CLICK_SCENARIO_COMPARE",
    ClickScenarioMigrate = "CLICK_SCENARIO_MIGRATE",
    ClickScenarioImport = "CLICK_SCENARIO_IMPORT",
    ClickScenarioJson = "CLICK_SCENARIO_JSON",
    ClickScenarioPdf = "CLICK_SCENARIO_PDF",
    ClickScenarioArchive = "CLICK_SCENARIO_ARCHIVE",
    ClickTestGenerated = "CLICK_TEST_GENERATED",
    ClickTestAdhoc = "CLICK_TEST_ADHOC",
    ClickTestFromFile = "CLICK_TEST_FROM_FILE",
    ClickTestGenerateFile = "CLICK_TEST_GENERATE_FILE",
    ClickTestHide = "CLICK_TEST_HIDE",
    ClickMoreScenarioDetails = "CLICK_MORE_SCENARIO_DETAILS",
    ClickRollUpPanel = "CLICK_ROLL_UP_PANEL",
    ClickExpandPanel = "CLICK_EXPAND_PANEL",
    ClickCollapsePanel = "CLICK_COLLAPSE_PANEL",
    MovePanel = "MOVE_PANEL",
    SearchNodesInScenario = "SEARCH_NODES_IN_SCENARIO",
    SearchComponentsInScenario = "SEARCH_COMPONENTS_IN_SCENARIO",
    ClickOlderVersion = "CLICK_OLDER_VERSION",
    ClickNewerVersion = "CLICK_NEWER_VERSION",
    ClickNodeDocumentation = "CLICK_NODE_DOCUMENTATION",
    ClickComponentsTab = "CLICK_COMPONENTS_TAB",
    ClickScenarioSave = "CLICK_SCENARIO_SAVE",
    ClickTestCounts = "CLICK_TEST_COUNTS",
    ClickScenarioCancel = "CLICK_SCENARIO_CANCEL",
    ClickScenarioArchiveToggle = "CLICK_SCENARIO_ARCHIVE_TOGGLE",
    ClickScenarioUnarchive = "CLICK_SCENARIO_UNARCHIVE",
    ClickScenarioCustomAction = "CLICK_SCENARIO_CUSTOM_ACTION",
    ClickScenarioCustomLink = "CLICK_SCENARIO_CUSTOM_LINK",
    KeyboardRangeSelectNodes = "KEYBOARD_RANGE_SELECT_NODES",
    KeyboardCopyNode = "KEYBOARD_COPY_NODE",
    KeyboardPasteNode = "KEYBOARD_PASTE_NODE",
    KeyboardCutNode = "KEYBOARD_CUT_NODE",
    KeyboardSelectAllNodes = "KEYBOARD_SELECT_ALL_NODES",
    KeyboardRedoScenarioChanges = "KEYBOARD_REDO_SCENARIO_CHANGES",
    KeyboardUndoScenarioChanges = "KEYBOARD_UNDO_SCENARIO_CHANGES",
    KeyboardDeleteNodes = "KEYBOARD_DELETE_NODES",
    KeyboardDeselectAllNodes = "KEYBOARD_DESELECT_ALL_NODES",
    KeyboardFocusSearchNodeField = "KEYBOARD_FOCUS_SEARCH_NODE_FIELD",
}

export const mapToolbarButtonToStatisticsEvent = (btnType: BuiltinButtonTypes | CustomButtonTypes): EventTrackingType | undefined => {
    switch (btnType) {
        case BuiltinButtonTypes.editCopy: {
            return EventTrackingType.ClickEditCopy;
        }
        case BuiltinButtonTypes.editDelete: {
            return EventTrackingType.ClickEditDelete;
        }
        case BuiltinButtonTypes.editLayout: {
            return EventTrackingType.ClickEditLayout;
        }
        case BuiltinButtonTypes.editPaste: {
            return EventTrackingType.ClickEditPaste;
        }
        case BuiltinButtonTypes.editRedo: {
            return EventTrackingType.ClickEditRedo;
        }
        case BuiltinButtonTypes.editUndo: {
            return EventTrackingType.ClickEditUndo;
        }
        case BuiltinButtonTypes.generateAndTest: {
            return EventTrackingType.ClickTestGenerateFile;
        }
        case BuiltinButtonTypes.processJSON: {
            return EventTrackingType.ClickScenarioJson;
        }
        case BuiltinButtonTypes.processArchive: {
            return EventTrackingType.ClickScenarioArchive;
        }
        case BuiltinButtonTypes.processCompare: {
            return EventTrackingType.ClickScenarioCompare;
        }
        case BuiltinButtonTypes.processDeploy: {
            return EventTrackingType.ClickActionDeploy;
        }
        case BuiltinButtonTypes.processMigrate: {
            return EventTrackingType.ClickScenarioMigrate;
        }
        case BuiltinButtonTypes.processImport: {
            return EventTrackingType.ClickScenarioImport;
        }
        case BuiltinButtonTypes.processPDF: {
            return EventTrackingType.ClickScenarioPdf;
        }
        case BuiltinButtonTypes.viewReset: {
            return EventTrackingType.ClickViewReset;
        }
        case BuiltinButtonTypes.viewZoomIn: {
            return EventTrackingType.ClickViewZoomIn;
        }
        case BuiltinButtonTypes.viewZoomOut: {
            return EventTrackingType.ClickViewZoomOut;
        }
        case BuiltinButtonTypes.testHide: {
            return EventTrackingType.ClickTestHide;
        }
        case BuiltinButtonTypes.testFromFile: {
            return EventTrackingType.ClickTestFromFile;
        }
        case BuiltinButtonTypes.processProperties: {
            return EventTrackingType.ClickScenarioProperties;
        }
        case BuiltinButtonTypes.testGenerate: {
            return EventTrackingType.ClickTestGenerated;
        }
        case BuiltinButtonTypes.testWithForm: {
            return EventTrackingType.ClickTestAdhoc;
        }
        case BuiltinButtonTypes.processSave: {
            return EventTrackingType.ClickScenarioSave;
        }
        case BuiltinButtonTypes.testCounts: {
            return EventTrackingType.ClickTestCounts;
        }
        case BuiltinButtonTypes.processCancel: {
            return EventTrackingType.ClickScenarioCancel;
        }
        case BuiltinButtonTypes.processArchiveToggle: {
            return EventTrackingType.ClickScenarioArchiveToggle;
        }
        case BuiltinButtonTypes.processUnarchive: {
            return EventTrackingType.ClickScenarioUnarchive;
        }
        case CustomButtonTypes.customAction: {
            return EventTrackingType.ClickScenarioCustomAction;
        }
        case CustomButtonTypes.customLink: {
            return EventTrackingType.ClickScenarioCustomLink;
        }
        default: {
            const exhaustiveCheck: never = btnType;

            return exhaustiveCheck;
        }
    }
};
