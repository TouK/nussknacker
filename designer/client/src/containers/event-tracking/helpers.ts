import { BuiltinButtonTypes, CustomButtonTypes } from "../../components/toolbarSettings/buttons/buttonsMap";
import type { TrackEventParams } from "./use-event-tracking";
import type { EventTrackingSelectorType, EventTrackingType } from "./use-register-tracking-events";
import { EventTrackingSelector } from "./use-register-tracking-events";

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
        case BuiltinButtonTypes.alignHorizontalLeft:
        case BuiltinButtonTypes.alignHorizontalCenter:
        case BuiltinButtonTypes.alignHorizontalRight:
        case BuiltinButtonTypes.alignVerticalTop:
        case BuiltinButtonTypes.alignVerticalCenter:
        case BuiltinButtonTypes.alignVerticalBottom:
        case BuiltinButtonTypes.distributeHorizontal:
        case BuiltinButtonTypes.distributeVertical:
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
        case BuiltinButtonTypes.processExport: {
            return EventTrackingSelector.ScenarioExport;
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
        case BuiltinButtonTypes.processRedeploy: {
            return EventTrackingSelector.ActionDeploy; // TODO: ActionRedeploy?
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
        case CustomButtonTypes.scenarioTest: {
            return EventTrackingSelector.ScenarioTest;
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
        case BuiltinButtonTypes.liveData: {
            return EventTrackingSelector.LiveData;
        }
        case BuiltinButtonTypes.snowSnow: {
            return EventTrackingSelector.SnowSnow;
        }
        default: {
            const exhaustiveCheck: never = btnType;

            return exhaustiveCheck;
        }
    }
};
