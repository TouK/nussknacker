import { setTestCaseAssertionResultsLoading, testAllScenarioTestCases, testScenarioWithTestCase } from "../../../actions/nk/testingActions";
import { useUserSettings } from "../../../common/useUserSettings";
import type { TestCase } from "../../../reducers/graph/testCase";
import { getSourceNodes } from "../../../reducers/selectors/graph";
import { getTestCases } from "../../../reducers/selectors/testCases";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import { readMockEnabled } from "../../graph/node-modal/node/NodeContent/TestingContentElements/useMockEnabled";
import { getProcessName } from "../../graph/node-modal/NodeDetailsContent/selectors";
import type { TestingDataRecords } from "../../modals/TestingDataRecords/Table";
import { safeParseExpression } from "../../modals/TestingDataRecords/utils";
import { useOpenNodeTestingTab } from "./useOpenNodeTestingTab";

export function useRunTestScenario() {
    const [showMockFieldOnEnrichers] = useUserSettings("node.showMockFieldOnEnrichers");

    const dispatch = useAppDispatch();
    const sourceNodes = useAppSelector(getSourceNodes);
    const scenarioName = useAppSelector(getProcessName);
    const allTestCases = useAppSelector(getTestCases);
    const openAddTestDataNode = useOpenNodeTestingTab();

    const getDisabledMockNodeIds = (testCase: TestCase) =>
        Object.keys(testCase.mocks ?? {}).filter((nodeId) => !readMockEnabled(scenarioName, nodeId));

    const runTest = (testCase: TestCase) => {
        const sourceNodeAvailable = sourceNodes?.[0];
        const testDataDefined = (safeParseExpression<TestingDataRecords[]>(testCase?.inputs) || []).length > 0;

        if (!testDataDefined && sourceNodeAvailable) {
            openAddTestDataNode(sourceNodeAvailable);
            return;
        }

        dispatch(setTestCaseAssertionResultsLoading(testCase.id));
        dispatch(testScenarioWithTestCase(testCase, showMockFieldOnEnrichers, getDisabledMockNodeIds(testCase)));
    };

    const runAllTests = () => {
        const disabledMockNodeIds = [...new Set(allTestCases.flatMap(getDisabledMockNodeIds))];
        dispatch(testAllScenarioTestCases(showMockFieldOnEnrichers, disabledMockNodeIds));
    };

    return { runTest, runAllTests };
}
