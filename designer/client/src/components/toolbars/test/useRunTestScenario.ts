import { testScenarioWithTestCase } from "../../../actions/nk/testingActions";
import { useUserSettings } from "../../../common/useUserSettings";
import type { TestCase } from "../../../reducers/graph/testCase";
import { getSourceNodes } from "../../../reducers/selectors/graph";
import { hasTestDataDefined } from "../../../reducers/selectors/testCases";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import { useOpenNodeTestingTab } from "./useOpenNodeTestingTab";

export function useRunTestScenario() {
    const [showMockFieldOnEnrichers] = useUserSettings("node.showMockFieldOnEnrichers");

    const dispatch = useAppDispatch();
    const sourceNodes = useAppSelector(getSourceNodes);
    const testDataDefined = useAppSelector(hasTestDataDefined);
    const openAddTestDataNode = useOpenNodeTestingTab();

    const runTest = (testCase: TestCase) => {
        const sourceNodeAvailable = sourceNodes?.[0];

        if (!testDataDefined && sourceNodeAvailable) {
            openAddTestDataNode(sourceNodeAvailable);
            return;
        }

        dispatch(testScenarioWithTestCase(testCase, showMockFieldOnEnrichers));
    };

    return { runTest };
}
