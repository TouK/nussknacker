import { isEqual } from "lodash";
import type { PropsWithChildren } from "react";
import React, { createContext, useContext, useEffect, useMemo, useState } from "react";

import type { StateForSelectTestResults } from "../../../common/TestResultUtils";
import TestResultUtils from "../../../common/TestResultUtils";
import { useUserSettings } from "../../../common/useUserSettings";
import { getTestResults } from "../../../reducers/selectors/testing";
import { useAppSelector } from "../../../store/storeHelpers";
import type { NodeId } from "../../../types/node";
import { useInputOutputContext } from "./io/InputOutputContext";
import TestErrors from "./tests/TestErrors";
import TestResultsComponent from "./tests/TestResults";
import TestResultsSelect from "./tests/TestResultsSelect";

export const Context = createContext<StateForSelectTestResults>(null);

export function useTestResults(): StateForSelectTestResults {
    const context = useContext(Context);
    if (!context) {
        throw "use only inside TestResultsWrapper!";
    }
    return context;
}

export function TestResultsWrapper({
    children,
    nodeId,
    showTestResults,
}: PropsWithChildren<{
    nodeId: NodeId;
    showTestResults?: boolean;
}>): React.JSX.Element {
    const results = useAppSelector(getTestResults);
    const nodeResults = useMemo(() => {
        if (showTestResults) {
            return TestResultUtils.resultsForNode(results, nodeId);
        }
        return null;
    }, [nodeId, results, showTestResults]);

    const [testResultsState, setTestResultsState] = useState<StateForSelectTestResults>(
        TestResultUtils.stateForSelectTestResults(nodeResults),
    );
    const [showInputsAndOutputs] = useUserSettings("node.showInputsAndOutputs");

    const io = useInputOutputContext();
    useEffect(() => {
        const chosenId = showInputsAndOutputs
            ? io?.state?.inputDataSetId || io?.state?.outputDataSetId
            : TestResultUtils.hasTestResults(nodeResults)
            ? TestResultUtils.availableContexts(nodeResults)[0].id
            : null;
        setTestResultsState((current) => {
            const testResults = TestResultUtils.stateForSelectTestResults(nodeResults, chosenId);
            return isEqual(testResults, current) ? current : testResults;
        });
    }, [nodeResults, io?.state?.inputDataSetId, io?.state?.outputDataSetId, showInputsAndOutputs]);

    return (
        <Context.Provider value={testResultsState}>
            {showInputsAndOutputs ? (
                <>
                    <TestErrors />
                    {children}
                </>
            ) : (
                <>
                    <TestResultsSelect results={nodeResults} value={testResultsState.testResultsIdToShow} onChange={setTestResultsState} />
                    <TestErrors />
                    {children}
                    <TestResultsComponent nodeId={nodeId} />
                </>
            )}
        </Context.Provider>
    );
}
