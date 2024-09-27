import { useDocumentEventListener } from "rooks";
import { useEventTracking } from "./use-event-tracking";
import { useSelector } from "react-redux";
import { getFeatureSettings } from "../../reducers/selectors/settings";

export const enum EventTrackingType {
    Search = "SEARCH",
    Filter = "FILTER",
    Sort = "SORT",
    Click = "CLICK",
    Keyboard = "KEYBOARD",
    Move = "MOVE",
    DoubleClick = "DOUBLE_CLICK",
    KeyboardAndClick = "KEYBOARD_AND_CLICK",
}

enum ClickEventsSelector {
    ComponentsTab = "COMPONENTS_TAB",
    GlobalMetricsTab = "GLOBAL_METRICS_TAB",
    NodeDocumentation = "NODE_DOCUMENTATION",
    NewerVersion = "NEWER_VERSION",
    OlderVersion = "OLDER_VERSION",
    CollapsePanel = "COLLAPSE_PANEL",
    ExpandPanel = "EXPAND_PANEL",
    ActionMetrics = "ACTION_METRICS",
    ScenarioFromComponentUsages = "SCENARIO_FROM_COMPONENT_USAGES",
    MoreScenarioDetails = "MORE_SCENARIO_DETAILS",
    ComponentUsages = "COMPONENT_USAGES",
    EditCopy = "EDIT_COPY",
    EditDelete = "EDIT_DELETE",
    EditLayout = "EDIT_LAYOUT",
    EditPaste = "EDIT_PASTE",
    EditRedo = "EDIT_REDO",
    EditUndo = "EDIT_UNDO",
    TestGenerateFile = "TEST_GENERATE_FILE",
    ScenarioJson = "SCENARIO_JSON",
    ScenarioArchive = "SCENARIO_ARCHIVE",
    ScenarioCompare = "SCENARIO_COMPARE",
    ActionDeploy = "ACTION_DEPLOY",
    ScenarioMigrate = "SCENARIO_MIGRATE",
    ScenarioImport = "SCENARIO_IMPORT",
    ScenarioPdf = "SCENARIO_PDF",
    ViewReset = "VIEW_RESET",
    ViewZoomIn = "VIEW_ZOOM_IN",
    ViewZoomOut = "VIEW_ZOOM_OUT",
    TestHide = "TEST_HIDE",
    TestFromFile = "TEST_FROM_FILE",
    ScenarioProperties = "SCENARIO_PROPERTIES",
    TestGenerated = "TEST_GENERATED",
    TestAdhoc = "TEST_ADHOC",
    ScenarioSave = "SCENARIO_SAVE",
    TestCounts = "TEST_COUNTS",
    ScenarioCancel = "SCENARIO_CANCEL",
    ScenarioArchiveToggle = "SCENARIO_ARCHIVE_TOGGLE",
    ScenarioUnarchive = "SCENARIO_UNARCHIVE",
    ScenarioCustomAction = "SCENARIO_CUSTOM_ACTION",
    ScenarioCustomLink = "SCENARIO_CUSTOM_LINK",
}

enum SearchEventsSelector {
    ComponentsInScenario = "COMPONENTS_IN_SCENARIO",
    ScenariosByName = "SCENARIOS_BY_NAME",
    NodesInScenario = "NODES_IN_SCENARIO",
    ComponentsByName = "COMPONENTS_BY_NAME",
    ComponentUsagesByName = "COMPONENT_USAGES_BY_NAME",
}

enum FilterEventsSelector {
    ComponentsByProcessingMode = "COMPONENTS_BY_PROCESSING_MODE",
    ComponentsByCategory = "COMPONENTS_BY_CATEGORY",
    ScenariosByOther = "SCENARIOS_BY_OTHER",
    ComponentsByUsages = "COMPONENTS_BY_USAGES",
    ComponentUsagesByStatus = "COMPONENT_USAGES_BY_STATUS",
    ComponentUsagesByCategory = "COMPONENT_USAGES_BY_CATEGORY",
    ScenariosByStatus = "SCENARIOS_BY_STATUS",
    ScenariosByProcessingMode = "SCENARIOS_BY_PROCESSING_MODE",
    ScenariosByCategory = "SCENARIOS_BY_CATEGORY",
    ScenariosByAuthor = "SCENARIOS_BY_AUTHOR",
    ScenariosByLabel = "SCENARIOS_BY_LABEL",
    ComponentUsagesByAuthor = "COMPONENT_USAGES_BY_AUTHOR",
    ComponentUsagesByOther = "COMPONENT_USAGES_BY_OTHER",
    ComponentsByGroup = "COMPONENTS_BY_GROUP",
}
enum KeyboardEventsSelector {
    FocusSearchNodeField = "FOCUS_SEARCH_NODE_FIELD",
    RangeSelectNodes = "RANGE_SELECT_NODES",
    SelectAllNodes = "SELECT_ALL_NODES",
    RedoScenarioChanges = "REDO_SCENARIO_CHANGES",
    UndoScenarioChanges = "UNDO_SCENARIO_CHANGES",
    DeleteNodes = "DELETE_NODES",
    DeselectAllNodes = "DESELECT_ALL_NODES",
    CopyNode = "COPY_NODE",
    PasteNode = "PASTE_NODE",
    CutNode = "CUT_NODE",
}

enum MoveEventsSelector {
    ToolbarPanel = "TOOLBAR_PANEL",
}

enum SortEventsSelector {
    ScenariosBySortOption = "SCENARIOS_BY_SORT_OPTION",
}

export const EventTrackingSelector = {
    ...ClickEventsSelector,
    ...FilterEventsSelector,
    ...SearchEventsSelector,
    ...SortEventsSelector,
    ...KeyboardEventsSelector,
    ...MoveEventsSelector,
} as const;

// To avoid an error from submodules during make-types we need to create an explicit union of enums here. In other case we need to export all Tracking Selector enums
export type EventTrackingSelectorType =
    | ClickEventsSelector
    | FilterEventsSelector
    | SearchEventsSelector
    | SortEventsSelector
    | KeyboardEventsSelector
    | MoveEventsSelector;

export const useRegisterTrackingEvents = () => {
    const { trackEvent, trackEventWithDebounce } = useEventTracking();
    const featuresSettings = useSelector(getFeatureSettings);
    const isEnabledForStatisticsEvent = (eventName: keyof DocumentEventMap) =>
        featuresSettings.usageStatisticsReports.enabled ? eventName : undefined;

    useDocumentEventListener(isEnabledForStatisticsEvent("click"), function (event: Event) {
        const path = event.composedPath() as HTMLElement[];

        for (const element of path) {
            const selector = element.dataset?.selector as EventTrackingSelectorType;

            if (Object.values(ClickEventsSelector).find((clickEvent) => clickEvent === selector)) {
                trackEvent({ selector, event: EventTrackingType.Click });
                break;
            }

            if (Object.values(FilterEventsSelector).find((filterEvent) => filterEvent === selector)) {
                const selected = (element as HTMLOptionElement).selected;
                if (selected) {
                    trackEvent({ selector, event: EventTrackingType.Filter });
                }
                break;
            }

            if (Object.values(SortEventsSelector).find((sortEvent) => sortEvent === selector)) {
                const selected = (element as HTMLOptionElement).selected;
                if (selected) {
                    trackEvent({ selector, event: EventTrackingType.Sort });
                }
                break;
            }
        }
    });

    useDocumentEventListener(isEnabledForStatisticsEvent("keyup"), function (event: KeyboardEvent) {
        const path = event.composedPath() as HTMLElement[];
        for (const element of path) {
            const selector = element.dataset?.selector as EventTrackingSelectorType;

            if (Object.values(SearchEventsSelector).find((searchEvent) => searchEvent === selector)) {
                const value = (element as HTMLInputElement).value;

                if (value) {
                    trackEventWithDebounce({ selector, event: EventTrackingType.Search });
                }

                break;
            }
        }
    });
};
