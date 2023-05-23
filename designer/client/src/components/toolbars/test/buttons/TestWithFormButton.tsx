import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { ReactComponent as Icon } from "../../../../assets/img/toolbarButtons/test-with-form.svg";
import {
    getProcessId,
    getProcessToDisplay,
    getTestCapabilities,
    getTestParameters,
    isLatestProcessVersion,
} from "../../../../reducers/selectors/graph";
import { useWindows, WindowKind } from "../../../../windowManager";
import { ToolbarButtonProps } from "../../types";
import ToolbarButton from "../../../toolbarComponents/ToolbarButton";
import _ from "lodash";
import { TestFormParameters } from "../../../../common/TestResultUtils";
import { testProcessWithParameters } from "../../../../actions/nk/displayTestResults";
import { GenericActionParameters } from "../../../modals/GenericActionDialog";
import { Expression } from "../../../../types";
import { SourceWithParametersTest } from "../../../../http/HttpService";
import { getFindAvailableVariables } from "../../../graph/node-modal/NodeDetailsContent/selectors";
import { displayTestCapabilities, fetchTestFormParameters } from "../../../../actions/nk";

type Props = ToolbarButtonProps;

function TestWithFormButton(props: Props) {
    const { disabled } = props;
    const { t } = useTranslation();
    const { open, inform } = useWindows();
    const processIsLatestVersion = useSelector(isLatestProcessVersion);
    const testCapabilities = useSelector(getTestCapabilities);
    const testFormParameters: TestFormParameters[] = useSelector(getTestParameters);
    const processId = useSelector(getProcessId);
    const processToDisplay = useSelector(getProcessToDisplay);
    const findAvailableVariables = useSelector(getFindAvailableVariables);
    const dispatch = useDispatch();

    const isAvailable = () =>
        !disabled && processIsLatestVersion && testCapabilities && testCapabilities.canTestWithForm;

    const [available, setAvailable] = useState(isAvailable);
    const [action, setAction] = useState(null);
    const [selectedSource, setSelectedSource] = useState(_.head(testFormParameters)?.sourceId);
    const [sourceParameters, setSourceParameters] = useState(updateParametersFromTestForm());
    const variableTypes = useMemo(
        () => findAvailableVariables?.(selectedSource),
        [findAvailableVariables, selectedSource]
    );

    function updateParametersFromTestForm(): { [key: string]: GenericActionParameters } {
        return (testFormParameters || []).reduce(
            (testFormObj, testFormParam) => ({
                ...testFormObj,
                [testFormParam.sourceId]: {
                    parameters: testFormParam.parameters,
                    parametersValues: (testFormParam.parameters || []).reduce(
                        (paramObj, param) => ({
                            ...paramObj,
                            [param.name]: param.defaultValue,
                        }),
                        {}
                    ),
                    onParamUpdate: (name: string) => (value: any) => onParamUpdate(testFormParam.sourceId, name, value),
                },
            }),
            {}
        );
    }

    function onParamUpdate(sourceId: string, name: string, value: any) {
        setSourceParameters((current) => ({
            ...current,
            [sourceId]: {
                ...current[sourceId],
                parametersValues: {
                    ...current[sourceId].parametersValues,
                    [name]: { expression: value, language: current[sourceId].parametersValues[name].language },
                },
            },
        }));
    }

    const onConfirmAction = useCallback(
        (paramValues) => {
            const parameters: { [paramName: string]: Expression } = sourceParameters[selectedSource].parameters.reduce(
                (obj, uiParam) => ({
                    ...obj,
                    [uiParam.name]: paramValues[uiParam.name],
                }),
                {}
            );
            const request: SourceWithParametersTest = {
                sourceId: selectedSource as string,
                parameterExpressions: parameters,
            };
            dispatch(testProcessWithParameters(processId, request, processToDisplay));
        },
        [sourceParameters, selectedSource]
    );

    useEffect(() => {
        if (available) dispatch(fetchTestFormParameters(processToDisplay));
    }, [available, processToDisplay]);

    useEffect(() => {
        setAvailable(isAvailable);
    }, [testCapabilities]);

    useEffect(() => {
        dispatch(displayTestCapabilities(processToDisplay));
    }, [processToDisplay, processIsLatestVersion]);

    //For now, we select first source and don't provide way to change it
    //Add support for multiple sources in next iteration (?)
    useEffect(() => {
        setSelectedSource(_.head(testFormParameters)?.sourceId);
        setSourceParameters(updateParametersFromTestForm());
    }, [testFormParameters]);

    useEffect(() => {
        setAction({
            variableTypes: variableTypes,
            layout: {
                name: "Test",
                confirmText: "Test",
            },
            ...sourceParameters[selectedSource],
            onConfirmAction,
        });
    }, [testFormParameters, sourceParameters, selectedSource]);

    const onButtonClick = useCallback(() => {
        const sourcesFound = Object.keys(sourceParameters).length;
        if (sourcesFound > 1) inform({ text: `Testing with form support only one source - found ${sourcesFound}.` });
        else
            open({
                title: t("dialog.title.testWithForm", "Test scenario"),
                isResizable: true,
                kind: WindowKind.genericAction,
                meta: action,
            });
    }, [action, sourceParameters]);

    return (
        <ToolbarButton
            name={t("panels.actions.test-with-form.button", "test form")}
            icon={<Icon />}
            disabled={!available || disabled}
            onClick={onButtonClick}
        />
    );
}

export default TestWithFormButton;
