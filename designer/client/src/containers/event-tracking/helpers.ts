import { TrackEventParams } from "./use-event-tracking";
import { BuiltinButtonTypes, CustomButtonTypes } from "../../components/toolbarSettings/buttons";

const selectorName = "data-selector";
const eventName = "data-statistic-event";

export const getEventTrackingProps = ({ selector, event }: TrackEventParams) => {
    return { [selectorName]: selector, [eventName]: event };
};

export const getEventStatisticName = ({ selector, event }: TrackEventParams): `${EventTrackingType}_${EventTrackingSelector}` => {
    return `${event}_${selector}`;
};

export const enum EventTrackingType {
    "SEARCH" = "SEARCH",
    "FILTER" = "FILTER",
    "SORT" = "SORT",
    "CLICK" = "CLICK",
    "KEYBOARD" = "KEYBOARD",
    "MOVE" = "MOVE",
}

export const enum EventTrackingSelector {
    ScenariosByName = "SCENARIOS_BY_NAME",
    ScenariosByStatus = "SCENARIOS_BY_STATUS",
    ScenariosByProcessingMode = "SCENARIOS_BY_PROCESSING_MODE",
    ScenariosByCategory = "SCENARIOS_BY_CATEGORY",
    ScenariosByAuthor = "SCENARIOS_BY_AUTHOR",
    ScenariosByOther = "SCENARIOS_BY_OTHER",
    SortScenarios = "SORT_SCENARIOS",
    ComponentsByName = "COMPONENTS_BY_NAME",
    ComponentsByGroup = "COMPONENTS_BY_GROUP",
    ComponentsByProcessingMode = "COMPONENTS_BY_PROCESSING_MODE",
    ComponentsByCategory = "COMPONENTS_BY_CATEGORY",
    ComponentsByMultipleCategories = "COMPONENTS_BY_MULTIPLE_CATEGORIES",
    ComponentsByUsages = "COMPONENTS_BY_USAGES",
    ComponentUsages = "COMPONENT_USAGES",
    ComponentUsagesByName = "COMPONENT_USAGES_BY_NAME",
    ComponentUsagesByStatus = "COMPONENT_USAGES_BY_STATUS",
    ComponentUsagesByCategory = "COMPONENT_USAGES_BY_CATEGORY",
    ComponentUsagesByAuthor = "COMPONENT_USAGES_BY_AUTHOR",
    ComponentUsagesByOther = "COMPONENT_USAGES_BY_OTHER",
    ScenarioFromComponentUsages = "SCENARIO_FROM_COMPONENT_USAGES",
    GlobalMetricsTab = "GLOBAL_METRICS_TAB",
    ActionDeploy = "ACTION_DEPLOY",
    ActionMetrics = "ACTION_METRICS",
    ViewZoomIn = "VIEW_ZOOM_IN",
    ViewZoomOut = "VIEW_ZOOM_OUT",
    ViewReset = "VIEW_RESET",
    EditUndo = "EDIT_UNDO",
    EditRedo = "EDIT_REDO",
    EditCopy = "EDIT_COPY",
    EditPaste = "EDIT_PASTE",
    EditDelete = "EDIT_DELETE",
    EditLayout = "EDIT_LAYOUT",
    ScenarioProperties = "SCENARIO_PROPERTIES",
    ScenarioCompare = "SCENARIO_COMPARE",
    ScenarioMigrate = "SCENARIO_MIGRATE",
    ScenarioImport = "SCENARIO_IMPORT",
    ScenarioJson = "SCENARIO_JSON",
    ScenarioPdf = "SCENARIO_PDF",
    ScenarioArchive = "SCENARIO_ARCHIVE",
    TestGenerated = "TEST_GENERATED",
    TestAdhoc = "TEST_ADHOC",
    TestFromFile = "TEST_FROM_FILE",
    TestGenerateFile = "TEST_GENERATE_FILE",
    TestHide = "TEST_HIDE",
    MoreScenarioDetails = "MORE_SCENARIO_DETAILS",
    ExpandPanel = "EXPAND_PANEL",
    CollapsePanel = "COLLAPSE_PANEL",
    ToolbarPanel = "TOOLBAR_PANEL",
    NodesInScenario = "NODES_IN_SCENARIO",
    ComponentsInScenario = "COMPONENTS_IN_SCENARIO",
    OlderVersion = "OLDER_VERSION",
    NewerVersion = "NEWER_VERSION",
    NodeDocumentation = "NODE_DOCUMENTATION",
    ComponentsTab = "COMPONENTS_TAB",
    ScenarioSave = "SCENARIO_SAVE",
    TestCounts = "TEST_COUNTS",
    ScenarioCancel = "SCENARIO_CANCEL",
    ScenarioArchiveToggle = "SCENARIO_ARCHIVE_TOGGLE",
    ScenarioUnarchive = "SCENARIO_UNARCHIVE",
    ScenarioCustomAction = "SCENARIO_CUSTOM_ACTION",
    ScenarioCustomLink = "SCENARIO_CUSTOM_LINK",
    RangeSelectNodes = "RANGE_SELECT_NODES",
    CopyNode = "COPY_NODE",
    PasteNode = "PASTE_NODE",
    CutNode = "CUT_NODE",
    SelectAllNodes = "SELECT_ALL_NODES",
    RedoScenarioChanges = "REDO_SCENARIO_CHANGES",
    UndoScenarioChanges = "UNDO_SCENARIO_CHANGES",
    DeleteNodes = "DELETE_NODES",
    DeselectAllNodes = "DESELECT_ALL_NODES",
    FocusSearchNodeField = "FOCUS_SEARCH_NODE_FIELD",
}

export const mapToolbarButtonToStatisticsEvent = (btnType: BuiltinButtonTypes | CustomButtonTypes): EventTrackingSelector | undefined => {
    switch (btnType) {
        case BuiltinButtonTypes.editCopy: {
            return EventTrackingSelector.EditCopy;
        }
        case BuiltinButtonTypes.editDelete: {
            return EventTrackingSelector.EditDelete;
        }
        case BuiltinButtonTypes.editLayout: {
            return EventTrackingSelector.EditLayout;
        }
        case BuiltinButtonTypes.editPaste: {
            return EventTrackingSelector.EditPaste;
        }
        case BuiltinButtonTypes.editRedo: {
            return EventTrackingSelector.EditRedo;
        }
        case BuiltinButtonTypes.editUndo: {
            return EventTrackingSelector.EditUndo;
        }
        case BuiltinButtonTypes.generateAndTest: {
            return EventTrackingSelector.TestGenerateFile;
        }
        case BuiltinButtonTypes.processJSON: {
            return EventTrackingSelector.ScenarioJson;
        }
        case BuiltinButtonTypes.processArchive: {
            return EventTrackingSelector.ScenarioArchive;
        }
        case BuiltinButtonTypes.processCompare: {
            return EventTrackingSelector.ScenarioCompare;
        }
        case BuiltinButtonTypes.processDeploy: {
            return EventTrackingSelector.ActionDeploy;
        }
        case BuiltinButtonTypes.processMigrate: {
            return EventTrackingSelector.ScenarioMigrate;
        }
        case BuiltinButtonTypes.processImport: {
            return EventTrackingSelector.ScenarioImport;
        }
        case BuiltinButtonTypes.processPDF: {
            return EventTrackingSelector.ScenarioPdf;
        }
        case BuiltinButtonTypes.viewReset: {
            return EventTrackingSelector.ViewReset;
        }
        case BuiltinButtonTypes.viewZoomIn: {
            return EventTrackingSelector.ViewZoomIn;
        }
        case BuiltinButtonTypes.viewZoomOut: {
            return EventTrackingSelector.ViewZoomOut;
        }
        case BuiltinButtonTypes.testHide: {
            return EventTrackingSelector.TestHide;
        }
        case BuiltinButtonTypes.testFromFile: {
            return EventTrackingSelector.TestFromFile;
        }
        case BuiltinButtonTypes.processProperties: {
            return EventTrackingSelector.ScenarioProperties;
        }
        case BuiltinButtonTypes.testGenerate: {
            return EventTrackingSelector.TestGenerated;
        }
        case BuiltinButtonTypes.testWithForm: {
            return EventTrackingSelector.TestAdhoc;
        }
        case BuiltinButtonTypes.processSave: {
            return EventTrackingSelector.ScenarioSave;
        }
        case BuiltinButtonTypes.testCounts: {
            return EventTrackingSelector.TestCounts;
        }
        case BuiltinButtonTypes.processCancel: {
            return EventTrackingSelector.ScenarioCancel;
        }
        case BuiltinButtonTypes.processArchiveToggle: {
            return EventTrackingSelector.ScenarioArchiveToggle;
        }
        case BuiltinButtonTypes.processUnarchive: {
            return EventTrackingSelector.ScenarioUnarchive;
        }
        case CustomButtonTypes.customAction: {
            return EventTrackingSelector.ScenarioCustomAction;
        }
        case CustomButtonTypes.customLink: {
            return EventTrackingSelector.ScenarioCustomLink;
        }
        default: {
            const exhaustiveCheck: never = btnType;

            return exhaustiveCheck;
        }
    }
};
