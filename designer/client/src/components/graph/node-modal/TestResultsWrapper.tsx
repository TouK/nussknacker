import type { PropsWithChildren } from "react";
import React, { createContext, useContext, useEffect, useMemo, useState } from "react";
import { useSelector } from "react-redux";

import type { StateForSelectTestResults } from "../../../common/TestResultUtils";
import TestResultUtils from "../../../common/TestResultUtils";
import { useUserSettings } from "../../../common/userSettings";
import { getTestResults } from "../../../reducers/selectors/graph";
import type { NodeId } from "../../../types";
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
}>): JSX.Element {
    const results = useSelector(getTestResults);
    const nodeResults = useMemo(() => {
        if (showTestResults) {
            return TestResultUtils.resultsForNode(results, nodeId);
        }
        return null;
    }, [nodeId, results, showTestResults]);

    const [testResultsState, setTestResultsState] = useState<StateForSelectTestResults>(
        TestResultUtils.stateForSelectTestResults(nodeResults),
    );
    const [userSettings] = useUserSettings();
    const showInputsAndOutputs = userSettings["node.showInputsAndOutputs"];

    const io = useInputOutputContext();
    useEffect(() => {
        const chosenId = showInputsAndOutputs
            ? io?.state?.inputDataSetId || io?.state?.outputDataSetId
            : TestResultUtils.hasTestResults(nodeResults)
            ? TestResultUtils.availableContexts(nodeResults)[0].id
            : null;
        setTestResultsState(TestResultUtils.stateForSelectTestResults(nodeResults, chosenId));
    }, [nodeResults, io?.state?.inputDataSetId, io?.state?.outputDataSetId, showInputsAndOutputs]);

    const [settings] = useUserSettings();
    return (
        <Context.Provider value={testResultsState}>
            {settings["node.showInputsAndOutputs"] ? (
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
