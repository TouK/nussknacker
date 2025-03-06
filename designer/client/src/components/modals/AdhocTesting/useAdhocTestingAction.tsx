import { head } from "lodash";
import { useCallback, useContext, useMemo } from "react";
import { useDispatch, useSelector } from "react-redux";
import { testProcessWithParameters } from "../../../actions/nk/displayTestResults";
import { getProcessingType, getProcessName, getScenarioGraph, getTestData, getTestParameters } from "../../../reducers/selectors/graph";
import { UIParameter } from "../../../types";
import { NodeContext } from "../../graph/node-modal/node/NodeDetails";
import { getFindAvailableVariables } from "../../graph/node-modal/NodeDetailsContent/selectors";
import { AdhocTestingParameters } from "./AdhocTestingDialog";
import { ActionValues } from "./AdhocTestingFormContext";

export type SourceParameters = {
    [key: string]: { parameters: UIParameter[] };
};

export function paramsListToRecord(parameters: UIParameter[]): ActionValues {
    return parameters.reduce(
        (paramObj, { defaultValue, name }) => ({
            ...paramObj,
            [name]: defaultValue,
        }),
        {},
    );
}

export function useSourceParameters() {
    const testFormParameters = useSelector(getTestParameters);
    const testData = useSelector(getTestData);

    //For now, we select first source and don't provide way to change it
    //Add support for multiple sources in next iteration (?)
    const sourceId = useMemo(() => head(testFormParameters)?.sourceId, [testFormParameters]);

    const sourceParameters = useMemo(
        (): SourceParameters =>
            testFormParameters.reduce((testFormObj, { parameters = [], sourceId }) => {
                const parametersValues = paramsListToRecord(parameters);
                return {
                    ...testFormObj,
                    [sourceId]: {
                        parameters,
                        parametersValues,
                    },
                };
            }, {}),
        [testFormParameters],
    );

    const lastUsedTestData = useMemo(() => testData[sourceId], [sourceId, testData]);

    return {
        sourceId,
        sourceParameters,
        lastUsedTestData,
    };
}

export function useAdhocTestingAction(): AdhocTestingParameters {
    const { sourceId, sourceParameters, lastUsedTestData: storedValues } = useSourceParameters();

    const parameters = useMemo<UIParameter[]>(() => sourceParameters[sourceId]?.parameters || [], [sourceId, sourceParameters]);

    const findAvailableVariables = useSelector(getFindAvailableVariables);
    const variableTypes = useMemo(() => findAvailableVariables?.(sourceId), [findAvailableVariables, sourceId]);

    const processingType = useSelector(getProcessingType);

    const initialValues = useMemo(() => storedValues || paramsListToRecord(parameters), [parameters, storedValues]);

    const dispatch = useDispatch();
    const scenarioName = useSelector(getProcessName);
    const scenarioGraph = useSelector(getScenarioGraph);
    const nodeContext = useContext(NodeContext);

    const onConfirmAction = useCallback(
        (parameterExpressions: ActionValues) => {
            const nodes = scenarioGraph.nodes.map((n) => (nodeContext?.id === n.id ? nodeContext : n));
            dispatch(
                testProcessWithParameters(
                    scenarioName,
                    {
                        sourceId,
                        parameterExpressions,
                    },
                    { ...scenarioGraph, nodes },
                ),
            );
        },
        [dispatch, scenarioName, sourceId, scenarioGraph, nodeContext],
    );

    return useMemo<AdhocTestingParameters>(
        () => ({
            parameters,
            variableTypes,
            processingType,
            scenarioName,
            initialValues,
            onConfirmAction,
            sourceId,
            scenarioGraph,
            previousTestData: storedValues,
        }),
        [parameters, variableTypes, processingType, scenarioName, initialValues, onConfirmAction, sourceId, scenarioGraph, storedValues],
    );
}
