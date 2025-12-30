import { head } from "lodash";
import { useCallback, useMemo } from "react";

import { testProcessWithParameters } from "../../../actions/nk/displayTestResults";
import { getProcessingType, getTestParameters } from "../../../reducers/selectors/graph";
import { getTestData } from "../../../reducers/selectors/testing";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import type { UIParameter } from "../../../types/definition";
import { getFindAvailableVariables } from "../../graph/node-modal/NodeDetailsContent/selectors";
import type { AdhocTestingParameters } from "./AdhocTestingDialog";
import type { ActionValues } from "./AdhocTestingFormContext";

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
    const testFormParameters = useAppSelector(getTestParameters);
    const testData = useAppSelector(getTestData);

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

    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const variableTypes = useMemo(() => findAvailableVariables?.(sourceId), [findAvailableVariables, sourceId]);

    const processingType = useAppSelector(getProcessingType);

    const initialValues = useMemo(() => storedValues || paramsListToRecord(parameters), [parameters, storedValues]);

    const dispatch = useAppDispatch();

    const onConfirmAction = useCallback(
        (parameterExpressions: ActionValues) => {
            dispatch(
                testProcessWithParameters({
                    sourceId,
                    parameterExpressions,
                }),
            );
        },
        [dispatch, sourceId],
    );

    return useMemo<AdhocTestingParameters>(
        () => ({
            parameters,
            variableTypes,
            processingType,
            initialValues,
            onConfirmAction,
            sourceId,
            previousTestData: storedValues,
        }),
        [parameters, variableTypes, processingType, initialValues, onConfirmAction, sourceId, storedValues],
    );
}
