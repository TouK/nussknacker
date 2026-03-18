import { testScenarioWithTestCase } from "../../../actions/nk/testingActions";
import { useUserSettings } from "../../../common/useUserSettings";
import type { TestCase } from "../../../reducers/graph/testCase";
import { getSourceNodes } from "../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import type { TestingDataRecords } from "../../modals/TestingDataRecords/Table";
import { safeParseExpression } from "../../modals/TestingDataRecords/utils";
import { useOpenNodeTestingTab } from "./useOpenNodeTestingTab";

export function useRunTestScenario() {
    const [showMockFieldOnEnrichers] = useUserSettings("node.showMockFieldOnEnrichers");

    const dispatch = useAppDispatch();
    const sourceNodes = useAppSelector(getSourceNodes);
    const openAddTestDataNode = useOpenNodeTestingTab();

    const runTest = (testCase: TestCase) => {
        const sourceNodeAvailable = sourceNodes?.[0];
        const testDataDefined = (safeParseExpression<TestingDataRecords[]>(testCase?.inputs) || []).length > 0;

        if (!testDataDefined && sourceNodeAvailable) {
            openAddTestDataNode(sourceNodeAvailable);
            return;
        }

        dispatch(testScenarioWithTestCase(testCase, showMockFieldOnEnrichers));
    };

    return { runTest };
}
