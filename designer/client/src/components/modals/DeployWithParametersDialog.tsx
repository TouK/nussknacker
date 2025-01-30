import { css, cx } from "@emotion/css";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import { getProcessName, getProcessVersionId, getScenarioGraph } from "../../reducers/selectors/graph";
import { getFeatureSettings } from "../../reducers/selectors/settings";
import { PromptContent, WindowKind } from "../../windowManager";
import CommentInput from "../comment/CommentInput";
import ProcessDialogWarnings from "./ProcessDialogWarnings";
import { FormHelperText, Typography } from "@mui/material";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { ActionNodeParameters } from "../../types/action";
import { AdvancedParametersSection } from "./AdvancedParametersSection";
import { mapValues } from "lodash";
import HttpService from "../../http/HttpService";
import { ActionParameter } from "./ActionParameter";
import { NodeTable } from "../graph/node-modal/NodeDetailsContent/NodeTable";
import { ToggleProcessActionModalData } from "./DeployProcessDialog";

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

    const actionParameters = useCallback(async () => {
        await HttpService.getActionParameters(processName, scenarioGraph);
    }, [processName, scenarioGraph]);
    const actionNodeParameters = actionParameters["DEPLOY"] || ([] as ActionNodeParameters[]);
    const [values, setValues] = useState(() => initialNodesData(actionNodeParameters));

    const [comment, setComment] = useState("");
    const [validationError, setValidationError] = useState("");
    const featureSettings = useSelector(getFeatureSettings);
    const deploymentCommentSettings = featureSettings.deploymentCommentSettings;

    const confirmAction = useCallback(async () => {
        try {
            await action(processName, processVersionId, comment, values);
            props.close();
        } catch (error) {
            setValidationError(error?.response?.data);
        }
    }, [action, comment, processName, props, processVersionId, values]);

    const { t } = useTranslation();
    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => props.close(), classname: LoadingButtonTypes.secondaryButton },
            { title: t("dialog.button.ok", "Ok"), action: () => confirmAction() },
        ],
        [confirmAction, props, t],
    );

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
                {actionNodeParameters && actionNodeParameters.length > 0 ? (
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
                ) : null}
                {actionNodeParameters.map((nodeParameters: ActionNodeParameters) => (
                    <AdvancedParametersSection key={nodeParameters.nodeId} nodeId={nodeParameters.nodeId}>
                        <NodeTable>
                            {Object.entries(nodeParameters.parameters).map(([paramName, paramConfig]) => {
                                return (
                                    <ActionParameter
                                        key={paramName}
                                        nodeName={nodeParameters.nodeId}
                                        propertyName={paramName}
                                        propertyConfig={paramConfig}
                                        errors={[]}
                                        onChange={(nodeId, paramName, newValue) => {
                                            setValues({
                                                ...values,
                                                [nodeId]: {
                                                    ...values[nodeId],
                                                    [paramName]: newValue,
                                                },
                                            });
                                        }}
                                        nodesData={values}
                                    />
                                );
                            })}
                        </NodeTable>
                    </AdvancedParametersSection>
                ))}
            </div>
        </PromptContent>
    );
}

export default DeployWithParametersDialog;
