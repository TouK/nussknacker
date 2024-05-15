import React from "react";
import { debounce } from "lodash";
import httpService from "../../http/HttpService";
import { PropsWithChildren, useCallback } from "react";

export type EventTrackingType =
    | "SEARCH_SCENARIOS_BY_NAME"
    | "FILTER_SCENARIOS_BY_STATUS"
    | "FILTER_SCENARIOS_BY_PROCESSING_MODE"
    | "FILTER_SCENARIOS_BY_CATEGORY"
    | "FILTER_SCENARIOS_BY_AUTHOR"
    | "FILTER_SCENARIOS_BY_OTHER"
    | "SORT_SCENARIOS"
    | "SEARCH_COMPONENTS_BY_NAME"
    | "FILTER_COMPONENTS_BY_GROUP"
    | "FILTER_COMPONENTS_BY_PROCESSING_MODE"
    | "FILTER_COMPONENTS_BY_CATEGORY"
    | "FILTER_COMPONENTS_BY_MULTIPLE_CATEGORIES"
    | "FILTER_COMPONENTS_BY_USAGES"
    | "CLICK_COMPONENT_USAGES"
    | "SEARCH_COMPONENT_USAGES_BY_NAME"
    | "FILTER_COMPONENT_USAGES_BY_STATUS"
    | "FILTER_COMPONENT_USAGES_BY_CATEGORY"
    | "FILTER_COMPONENT_USAGES_BY_AUTHOR"
    | "FILTER_COMPONENT_USAGES_BY_OTHER"
    | "CLICK_SCENARIO_FROM_COMPONENT_USAGES"
    | "CLICK_GLOBAL_METRICS_TAB"
    | "CLICK_ACTION_DEPLOY"
    | "CLICK_ACTION_METRICS"
    | "CLICK_VIEW_ZOOM_IN"
    | "CLICK_VIEW_ZOOM_OUT"
    | "CLICK_VIEW_RESET"
    | "CLICK_EDIT_UNDO"
    | "CLICK_EDIT_REDO"
    | "CLICK_EDIT_COPY"
    | "CLICK_EDIT_PASTE"
    | "CLICK_EDIT_DELETE"
    | "CLICK_EDIT_LAYOUT"
    | "CLICK_SCENARIO_PROPERTIES"
    | "CLICK_SCENARIO_COMPARE"
    | "CLICK_SCENARIO_MIGRATE"
    | "CLICK_SCENARIO_IMPORT"
    | "CLICK_SCENARIO_JSON"
    | "CLICK_SCENARIO_PDF"
    | "CLICK_SCENARIO_ARCHIVE"
    | "CLICK_TEST_GENERATED"
    | "CLICK_TEST_ADHOC"
    | "CLICK_TEST_FROM_FILE"
    | "CLICK_TEST_GENERATE_FILE"
    | "CLICK_TEST_HIDE"
    | "CLICK_MORE_SCENARIO_DETAILS"
    | "CLICK_ROLL_UP_PANEL"
    | "CLICK_EXPAND_PANEL"
    | "CLICK_COLLAPSE_PANEL"
    | "MOVE_PANEL"
    | "SEARCH_NODES_IN_SCENARIO"
    | "SEARCH_COMPONENTS_IN_SCENARIO"
    | "CLICK_OLDER_VERSION"
    | "CLICK_NEWER_VERSION"
    | "FIRED_KEY_STROKE"
    | "CLICK_NODE_DOCUMENTATION"
    | "CLICK_COMPONENTS_TAB"
    | "CLICK_SCENARIO_SAVE"
    | "CLICK_TEST_COUNTS"
    | "CLICK_SCENARIO_CANCEL"
    | "CLICK_SCENARIO_ARCHIVE_TOGGLE"
    | "CLICK_SCENARIO_UNARCHIVE"
    | "CLICK_SCENARIO_CUSTOM_ACTION"
    | "CLICK_SCENARIO_CUSTOM_LINK"
    | "KEYBOARD_SELECT_NODES"
    | "KEYBOARD_COPY_NODE"
    | "KEYBOARD_PASTE_NODE"
    | "KEYBOARD_CUT_NODE"
    | "KEYBOARD_SELECT_ALL_NODES"
    | "KEYBOARD_REDO_SCENARIO_CHANGES"
    | "KEYBOARD_UNDO_SCENARIO_CHANGES"
    | "KEYBOARD_DELETE_NODES"
    | "KEYBOARD_DESELECT_ALL_NODES"
    | "KEYBOARD_FOCUS_SEARCH_NODE_FIELD";

export const trackEvent1 = async ({ type }: TrackEventParams) => {
    await httpService.sendStatistics([{ name: type }]);
};

export type TrackEventParams = { type: EventTrackingType };
export const useEventTracking = () => {
    const trackEvent = async ({ type }: TrackEventParams) => {
        await httpService.sendStatistics([{ name: type }]);
    };

    const trackEventWithDebounce = useCallback(
        debounce((event: TrackEventParams) => trackEvent(event), 1500),
        [],
    );

    const WithEventTracking = ({ children, event }: PropsWithChildren<{ event: TrackEventParams }>) => {
        const onClick = (event: TrackEventParams) => {
            trackEvent(event);
        };

        return <div onClick={() => onClick(event)}>{children}</div>;
    };
    return { trackEvent, trackEventWithDebounce, WithEventTracking };
};
