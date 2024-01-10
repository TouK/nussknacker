import { Layout } from "../../actions/nk";
import { Process } from "../../components/Process/types";
import { TestCapabilities, TestResults, TestFormParameters } from "../../common/TestResultUtils";

export interface NodeCounts {
    errors?: number;
    all?: number;
    fragmentCounts?: ProcessCounts;
}

export type ProcessCounts = Record<string, NodeCounts>;

export type GraphState = {
    scenarioLoading: boolean;
    process?: Process;
    selectionState?: string[];
    layout: Layout;
    testCapabilities?: TestCapabilities;
    testFormParameters?: TestFormParameters[];
    testResults: TestResults;
    processCounts: ProcessCounts;
    unsavedNewName: string | null;
};
