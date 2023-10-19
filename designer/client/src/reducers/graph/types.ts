import { Layout } from "../../actions/nk";
import { ProcessType } from "../../components/Process/types";
import { Process } from "../../types";
import { TestCapabilities, TestResults, TestFormParameters } from "../../common/TestResultUtils";

export interface NodeCounts {
    errors?: number;
    all?: number;
    fragmentCounts?: ProcessCounts;
}

export type ProcessCounts = Record<string, NodeCounts>;

export type GraphState = {
    graphLoading: boolean;
    fetchedProcessDetails?: ProcessType;
    processToDisplay?: Process;
    selectionState?: string[];
    layout: Layout;
    testCapabilities?: TestCapabilities;
    testFormParameters?: TestFormParameters[];
    testResults: TestResults;
    processCounts: ProcessCounts;
    unsavedNewName: string | null;
    restoreHistory?: boolean;
};
