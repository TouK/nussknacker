import type { Layout, RefreshData } from "../../actions/nk";
import type { StickyNote } from "../../common/StickyNote";
import type { TestCapabilities, TestFormParameters, TestResults } from "../../common/TestResultUtils";
import type { Scenario } from "../../components/Process/types";
import type { SourceWithParametersTest } from "../../http/HttpService";

export interface NodeCounts {
    errors?: number;
    all?: number;
    fragmentCounts?: ProcessCounts;
}

export type ProcessCounts = Record<string, NodeCounts>;

type Source = SourceWithParametersTest["sourceId"];
export type SourceTestData = SourceWithParametersTest["parameterExpressions"];
export type TestData = Record<Source, SourceTestData>;

export type GraphState = {
    scenarioLoading: boolean;
    scenario?: Scenario;
    stickyNotes?: StickyNote[];
    selectionState?: string[];
    layout: Layout;
    testCapabilities?: TestCapabilities;
    testType?: string;
    testFormParameters?: TestFormParameters[];
    testResults: TestResults;
    testResultsLoading?: boolean;
    testData?: TestData;
    processCountsRefresh?: RefreshData;
    processCounts: ProcessCounts;
};
