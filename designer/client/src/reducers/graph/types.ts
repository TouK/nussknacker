import { Layout, RefreshData } from "../../actions/nk";
import { StickyNote } from "../../common/StickyNote";
import { TestCapabilities, TestFormParameters, TestResults } from "../../common/TestResultUtils";
import { Scenario } from "../../components/Process/types";
import { SourceWithParametersTest } from "../../http/HttpService";

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
    testFormParameters?: TestFormParameters[];
    testResults: TestResults;
    testResultsLoading?: boolean;
    testData?: TestData;
    processCountsRefresh?: RefreshData;
    processCounts: ProcessCounts;
    unsavedNewName: string | null;
};
