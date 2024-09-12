import { Layout, RefreshData } from "../../actions/nk";
import { Scenario } from "../../components/Process/types";
import { TestCapabilities, TestFormParameters, TestResults } from "../../common/TestResultUtils";
import { ActivityParameters } from "../../types/activity";
import { StickyNote } from "../../common/StickyNote";

export interface NodeCounts {
    errors?: number;
    all?: number;
    fragmentCounts?: ProcessCounts;
}

export type ProcessCounts = Record<string, NodeCounts>;

export type GraphState = {
    scenarioLoading: boolean;
    scenario?: Scenario;
    stickyNotes?: StickyNote[];
    selectionState?: string[];
    layout: Layout;
    activityParameters?: ActivityParameters;
    testCapabilities?: TestCapabilities;
    testFormParameters?: TestFormParameters[];
    testResults: TestResults;
    processCountsRefresh?: RefreshData;
    processCounts: ProcessCounts;
    unsavedNewName: string | null;
};
