import type { Layout, RefreshData } from "../../actions/nk";
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

export type GraphState = {
    scenarioLoading: boolean;
    scenario?: Scenario;
    selectionState?: string[];
    layout: Layout;
    testCapabilities?: TestCapabilities;
    testFormParameters?: TestFormParameters[];
    visibleDataType?: VisibleDataType | null;
    testing: {
        testResults: TestResultsDto;
        testResultsLoading?: boolean;
        testData?: TestData;
        testingDataRecords?: TestingDataRecords[];
    };
    processCountsRefresh?: RefreshData;
    processCounts: ProcessCounts;
};
