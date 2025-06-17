import type { Layout, RefreshData } from "../../actions/nk";
import type { PerformedTestType } from "../../actions/nk/displayTestResults";
import type { TestCapabilities, TestFormParameters } from "../../common/TestResultUtils";
import type { Scenario } from "../../components/Process/types";
import type { SourceWithParametersTest } from "../../http/HttpService";
import type { ProcessCounts, TestResultsDto } from "../../http/resultsWithCountsDto";

type Source = SourceWithParametersTest["sourceId"];
export type SourceTestData = SourceWithParametersTest["parameterExpressions"];
export type TestData = Record<Source, SourceTestData>;

export type GraphState = {
    scenarioLoading: boolean;
    scenario?: Scenario;
    selectionState?: string[];
    layout: Layout;
    testCapabilities?: TestCapabilities;
    testType?: string;
    performedTestType?: PerformedTestType;
    testFormParameters?: TestFormParameters[];
    visibleDataType?: "live" | "test" | "counts" | null;
    testResults: TestResultsDto;
    testResultsLoading?: boolean;
    testData?: TestData;
    processCountsRefresh?: RefreshData;
    processCounts: ProcessCounts;
};
