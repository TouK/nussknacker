import { css, cx } from "@emotion/css";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import { getProcessName, getProcessVersionId, getScenarioGraph } from "../../reducers/selectors/graph";
import { getFeatureSettings } from "../../reducers/selectors/settings";
import { PromptContent, WindowKind } from "../../windowManager";
import CommentInput from "../comment/CommentInput";
import ProcessDialogWarnings from "./ProcessDialogWarnings";
import { FormHelperText, Typography } from "@mui/material";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { ScenarioActionResultType } from "../toolbars/scenarioActions/buttons/types";
import { ActionNodeParameters } from "../../types/action";
import { AdvancedParametersSection } from "./AdvancedParametersSection";
import { mapValues } from "lodash";
import HttpService from "../../http/HttpService";
import { ActionParameter } from "./ActionParameter";
import { NodeTable } from "../graph/node-modal/NodeDetailsContent/NodeTable";
import { ToggleProcessActionModalData } from "./DeployProcessDialog";
import LoaderSpinner from "../spinner/Spinner";
import { useErrorBoundary } from "react-error-boundary";

function initialNodesData(params: ActionNodeParameters[]) {
    return params.reduce(
        (paramObj, { nodeId, parameters }) => ({
            ...paramObj,
            [nodeId]: mapValues(parameters, (value) => value.defaultValue || ""),
        }),
        {},
    );
}

export function DeployWithParametersDialog(props: WindowContentProps<WindowKind, ToggleProcessActionModalData>): JSX.Element {
    // TODO: get rid of meta
    const {
        meta: { action, displayWarnings },
    } = props.data;
    const processName = useSelector(getProcessName);
    const processVersionId = useSelector(getProcessVersionId);
    const scenarioGraph = useSelector(getScenarioGraph);
    const { showBoundary } = useErrorBoundary();
    const [isLoading, setIsLoading] = useState<boolean>(false);
    const [parametersDefinition, setParametersDefinition] = useState([]);
    const [parametersValues, setParametersValues] = useState({});

    const getActionParameters = useCallback(async () => {
        setIsLoading(true);
        await HttpService.getActionParameters(processName, scenarioGraph)
            .then((response) => {
                const definition = response.data.actionNameToParameters["DEPLOY"] || ([] as ActionNodeParameters[]);
                const initialValues = initialNodesData(definition);
                setParametersDefinition(definition);
                setParametersValues(initialValues);
            })
            .catch((error) => {
                showBoundary(error);
            })
            .finally(() => {
                setIsLoading(false);
            });
    }, [processName, scenarioGraph, showBoundary]);

    useEffect(() => {
        getActionParameters();
    }, [processName, scenarioGraph, getActionParameters]);

    const [comment, setComment] = useState("");
    const [validationError, setValidationError] = useState("");
    const featureSettings = useSelector(getFeatureSettings);
    const deploymentCommentSettings = featureSettings.deploymentCommentSettings;

    const confirmAction = useCallback(async () => {
        const response = await action(processName, processVersionId, comment, parametersValues);
        switch (response.scenarioActionResultType) {
            case ScenarioActionResultType.Success:
            case ScenarioActionResultType.UnhandledError:
                props.close();
                break;
            case ScenarioActionResultType.ValidationError:
                setValidationError(response.msg);
                break;
            default:
                console.log("Unexpected result type:", response.scenarioActionResultType);
                break;
        }
    }, [action, comment, processName, props, processVersionId, parametersValues]);

    const { t } = useTranslation();
    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => props.close(), classname: LoadingButtonTypes.secondaryButton },
            { title: t("dialog.button.ok", "Ok"), action: () => confirmAction() },
        ],
        [confirmAction, props, t],
    );

    if (isLoading) {
        return <LoaderSpinner show={true} />;
    }

    return (
        <PromptContent {...props} buttons={buttons}>
            <div className={cx("modalContentDark")}>
                <Typography variant={"h3"}>{props.data.title}</Typography>
                {displayWarnings && <ProcessDialogWarnings />}
                <CommentInput
                    onChange={(e) => setComment(e.target.value)}
                    value={comment}
                    defaultValue={deploymentCommentSettings?.exampleComment}
                    className={cx(
                        css({
                            minWidth: 600,
                            minHeight: 80,
                        }),
                    )}
                    autoFocus
                />
                <FormHelperText title={validationError} error>
                    {validationError}
                </FormHelperText>
                {parametersDefinition ? (
                    <div>
                        <Typography
                            sx={(theme) => ({
                                color: theme.palette.primary.main,
                                pt: "1em",
                                textTransform: "uppercase",
                                textDecoration: "none",
                            })}
                        >
                            {t("dialog.advancedParameters.title", "Advanced parameters")}
                        </Typography>
                        {parametersDefinition.map((nodeParameters: ActionNodeParameters) => (
                            <AdvancedParametersSection key={nodeParameters.nodeId} nodeId={nodeParameters.nodeId}>
                                <NodeTable>
                                    {Object.entries(nodeParameters.parameters).map(([paramName, paramConfig]) => {
                                        return (
                                            <ActionParameter
                                                key={paramName}
                                                nodeId={nodeParameters.nodeId}
                                                parameterName={paramName}
                                                parameterConfig={paramConfig}
                                                errors={[]}
                                                onChange={(nodeId, parameterName, newValue) => {
                                                    setParametersValues({
                                                        ...parametersValues,
                                                        [nodeId]: {
                                                            ...parametersValues[nodeId],
                                                            [parameterName]: newValue,
                                                        },
                                                    });
                                                }}
                                                parameterValue={parametersValues[nodeParameters.nodeId][paramName] || ""}
                                            />
                                        );
                                    })}
                                </NodeTable>
                            </AdvancedParametersSection>
                        ))}
                    </div>
                ) : null}
            </div>
        </PromptContent>
    );
}

export default DeployWithParametersDialog;
