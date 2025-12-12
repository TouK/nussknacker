import type { RefreshData } from "../../actions/nk/displayProcessCounts";
import type { Layout } from "../../actions/nk/ui/layout";
import type { TestCapabilities, TestFormParameters } from "../../common/TestResultUtils";
import type { TestingDataRecords } from "../../components/modals/TestingDataRecords/Table";
import type { Scenario } from "../../components/Process/types";
import type { SourceWithParametersTest } from "../../http/HttpService/types";
import type { ProcessCounts, TestResultsDto } from "../../http/resultsWithCountsDto";

type Source = SourceWithParametersTest["sourceId"];
export type SourceTestData = SourceWithParametersTest["parameterExpressions"];
export type TestData = Record<Source, SourceTestData>;

export enum VisibleDataType {
    live = "live",
    test = "test",
    counts = "counts",
}

type TestingState = {
    testResults: TestResultsDto;
    testResultsLoading?: boolean;
    testData?: TestData;
    testingDataRecords?: TestingDataRecords[];
};
export type GraphState = {
    scenarioLoading: boolean;
    scenario?: Scenario;
    selectionState?: string[];
    layout: Layout;
    testCapabilities?: TestCapabilities;
    testFormParameters?: TestFormParameters[];
    visibleDataType?: VisibleDataType | null;
    testing: TestingState;
    processCountsRefresh?: RefreshData;
    processCounts: ProcessCounts;
};
