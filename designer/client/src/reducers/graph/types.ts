import type { RefreshData } from "../../actions/nk/displayProcessCounts";
import type { Layout } from "../../actions/nk/ui/layout";
import type { TestCapabilities, TestFormParameters } from "../../common/TestResultUtils";
import type { Scenario } from "../../components/Process/types";
import type { ProcessCounts } from "../../http/resultsWithCountsDto";
import type { TestingState } from "./testing";

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
    testing: TestingState;
    processCountsRefresh?: RefreshData;
    processCounts: ProcessCounts;
};
