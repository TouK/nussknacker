import React from "react";
import { BuiltinButtonTypes, CustomButtonTypes, TOOLBAR_BUTTONS_MAP, ToolbarButton } from "./buttons";
import { ToolbarConfig } from "./types";
import { TOOLBAR_COMPONENTS_MAP } from "./TOOLBAR_COMPONENTS_MAP";
import { EventTrackingType, useEventTracking } from "../../containers/event-tracking";

const mapToolbarButtonToStatisticsEvent = (btnType: BuiltinButtonTypes | CustomButtonTypes): EventTrackingType | undefined => {
    switch (btnType) {
        case BuiltinButtonTypes.editCopy: {
            return "CLICK_EDIT_COPY";
        }
        case BuiltinButtonTypes.editDelete: {
            return "CLICK_EDIT_DELETE";
        }
        case BuiltinButtonTypes.editLayout: {
            return "CLICK_EDIT_LAYOUT";
        }
        case BuiltinButtonTypes.editPaste: {
            return "CLICK_EDIT_PASTE";
        }
        case BuiltinButtonTypes.editRedo: {
            return "CLICK_EDIT_REDO";
        }
        case BuiltinButtonTypes.editUndo: {
            return "CLICK_EDIT_UNDO";
        }
        case BuiltinButtonTypes.generateAndTest: {
            return "CLICK_TEST_GENERATE_FILE";
        }
        case BuiltinButtonTypes.processJSON: {
            return "CLICK_SCENARIO_JSON";
        }
        case BuiltinButtonTypes.processArchive: {
            return "CLICK_SCENARIO_ARCHIVE";
        }
        case BuiltinButtonTypes.processCompare: {
            return "CLICK_SCENARIO_COMPARE";
        }
        case BuiltinButtonTypes.processDeploy: {
            return "CLICK_ACTION_DEPLOY";
        }
        case BuiltinButtonTypes.processMigrate: {
            return "CLICK_SCENARIO_MIGRATE";
        }
        case BuiltinButtonTypes.processImport: {
            return "CLICK_SCENARIO_IMPORT";
        }
        case BuiltinButtonTypes.processPDF: {
            return "CLICK_SCENARIO_PDF";
        }
        case BuiltinButtonTypes.viewReset: {
            return "CLICK_VIEW_RESET";
        }
        case BuiltinButtonTypes.viewZoomIn: {
            return "CLICK_VIEW_ZOOM_IN";
        }
        case BuiltinButtonTypes.viewZoomOut: {
            return "CLICK_VIEW_ZOOM_OUT";
        }
        case BuiltinButtonTypes.testHide: {
            return "CLICK_TEST_HIDE";
        }
        case BuiltinButtonTypes.testFromFile: {
            return "CLICK_TEST_FROM_FILE";
        }
        case BuiltinButtonTypes.processProperties: {
            return "CLICK_SCENARIO_PROPERTIES";
        }
        case BuiltinButtonTypes.testGenerate: {
            return "CLICK_TEST_GENERATED";
        }
        case BuiltinButtonTypes.testWithForm: {
            return "CLICK_TEST_ADHOC";
        }
        case BuiltinButtonTypes.processSave: {
            return "CLICK_SCENARIO_SAVE";
        }
        case BuiltinButtonTypes.testCounts: {
            return "CLICK_TEST_COUNTS";
        }
        case BuiltinButtonTypes.processCancel: {
            return "CLICK_SCENARIO_CANCEL";
        }
        case BuiltinButtonTypes.processArchiveToggle: {
            return "CLICK_SCENARIO_ARCHIVE_TOGGLE";
        }
        case BuiltinButtonTypes.processUnarchive: {
            return "CLICK_SCENARIO_UNARCHIVE";
        }
        case CustomButtonTypes.customAction: {
            return "CLICK_SCENARIO_CUSTOM_ACTION";
        }
        case CustomButtonTypes.customLink: {
            return "CLICK_SCENARIO_CUSTOM_LINK";
        }
        default: {
            const exhaustiveCheck: never = btnType;

            return exhaustiveCheck;
        }
    }
};

function ButtonSelector(btn: ToolbarButton, i: number) {
    const { WithEventTracking } = useEventTracking();
    // this type have to be specified to avoid type errors
    const Component: React.ComponentType<ToolbarButton> = TOOLBAR_BUTTONS_MAP[btn.type];
    const statisticEventType = mapToolbarButtonToStatisticsEvent(btn.type);

    return statisticEventType ? (
        <WithEventTracking event={{ type: mapToolbarButtonToStatisticsEvent(btn.type) }}>
            <Component key={i} {...btn} />
        </WithEventTracking>
    ) : (
        <Component key={i} {...btn} />
    );
}

export const ToolbarSelector = ({ buttons, ...props }: ToolbarConfig): JSX.Element => {
    const Component = TOOLBAR_COMPONENTS_MAP[props.id] || TOOLBAR_COMPONENTS_MAP.DefaultPanel;
    return <Component {...props}>{buttons?.map(ButtonSelector)}</Component>;
};
