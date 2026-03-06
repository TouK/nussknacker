import { calculateAssertionResultsSummary } from "../../../../../containers/assertions/assertionResultsUtils";
import { getTestAssertionResults } from "../../../../../reducers/selectors/testing";
import { useAppSelector } from "../../../../../store/storeHelpers";

export const useAssertionResultsSummary = () => {
    const testAssertionResults = useAppSelector(getTestAssertionResults);
    const allResults = Object.values(testAssertionResults).flat();
    const { hasResult, failedCount } = calculateAssertionResultsSummary(allResults);
    const assertionsIsSuccess = hasResult && failedCount === 0;

    return { hasResult, assertionsIsSuccess };
};
