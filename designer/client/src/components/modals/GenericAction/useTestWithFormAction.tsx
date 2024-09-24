import { GenericAction, GenericActionParameters } from "./GenericActionDialog";
import { useCallback, useMemo, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { getFindAvailableVariables } from "../../graph/node-modal/NodeDetailsContent/selectors";
import { getProcessingType, getProcessName, getScenarioGraph, getTestParameters } from "../../../reducers/selectors/graph";
import { testProcessWithParameters } from "../../../actions/nk/displayTestResults";
import { UIParameter } from "../../../types";
import { head } from "lodash";
import { ActionValues } from "./GenericActionFormContext";

export type SourceParameters = {
    [key: string]: GenericActionParameters;
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

    return {
        sourceId,
        sourceParameters,
    };
}

export function useTestWithFormAction(): GenericAction {
    const { sourceId, sourceParameters } = useSourceParameters();

    const parameters = useMemo<UIParameter[]>(() => sourceParameters[sourceId]?.parameters || [], [sourceId, sourceParameters]);

    const findAvailableVariables = useSelector(getFindAvailableVariables);
    const variableTypes = useMemo(() => findAvailableVariables?.(sourceId), [findAvailableVariables, sourceId]);

    const processingType = useSelector(getProcessingType);

    const [storedValues, setStoredValues] = useState<ActionValues>();
    const initialValues = useMemo(() => storedValues || paramsListToRecord(parameters), [parameters, storedValues]);

    const dispatch = useDispatch();
    const scenarioName = useSelector(getProcessName);
    const scenarioGraph = useSelector(getScenarioGraph);
    const onConfirmAction = useCallback(
        (parameterExpressions: ActionValues) => {
            setStoredValues(parameterExpressions);
            dispatch(
                testProcessWithParameters(
                    scenarioName,
                    {
                        sourceId,
                        parameterExpressions,
                    },
                    scenarioGraph,
                ),
            );
        },
        [sourceId, dispatch, scenarioName, scenarioGraph],
    );

    return useMemo<GenericAction>(
        () => ({
            parameters,
            variableTypes,
            processingType,
            scenarioName,
            initialValues,
            onConfirmAction,
            sourceId,
            scenarioGraph,
        }),
        [initialValues, onConfirmAction, parameters, sourceId, scenarioGraph, processingType, scenarioName, variableTypes],
    );
}
