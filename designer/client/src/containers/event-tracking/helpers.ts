import { TrackEventParams } from "./use-event-tracking";
import { BuiltinButtonTypes } from "../../components/toolbarSettings/buttons/BuiltinButtonTypes";
import { CustomButtonTypes } from "../../components/toolbarSettings/buttons/CustomButtonTypes";
import { EventTrackingSelector, EventTrackingSelectorType, EventTrackingType } from "./use-register-tracking-events";

const selectorName = "data-selector";

export const getEventTrackingProps = ({ selector }: Pick<TrackEventParams, "selector">) => {
    return { [selectorName]: selector };
};

export const getEventStatisticName = ({ selector, event }: TrackEventParams): `${EventTrackingType}_${EventTrackingSelectorType}` => {
    return `${event}_${selector}`;
};

export const mapToolbarButtonToStatisticsEvent = (
    btnType: BuiltinButtonTypes | CustomButtonTypes,
): EventTrackingSelectorType | undefined => {
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
        case CustomButtonTypes.adhocTesting: {
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
        case BuiltinButtonTypes.processRunOffSchedule: {
            return EventTrackingSelector.ScenarioRunOffSchedule;
        }
        case BuiltinButtonTypes.processArchiveToggle: {
            return EventTrackingSelector.ScenarioArchiveToggle;
        }
        case BuiltinButtonTypes.processUnarchive: {
            return EventTrackingSelector.ScenarioUnarchive;
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
