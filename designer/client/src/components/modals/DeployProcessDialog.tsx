import { css, cx } from "@emotion/css";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { getActivityParameters, getProcessName } from "../../reducers/selectors/graph";
import { getFeatureSettings } from "../../reducers/selectors/settings";
import { ProcessName } from "../Process/types";
import { PromptContent, WindowKind } from "../../windowManager";
import CommentInput from "../comment/CommentInput";
import ProcessDialogWarnings from "./ProcessDialogWarnings";
import { FormHelperText, Typography } from "@mui/material";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { ActivityNodeParameters } from "../../types/activity";
import { AdvancedParametersSection } from "./AdvancedParametersSection";
import { mapValues } from "lodash";
import { NodesDeploymentData } from "../../http/HttpService";
import { ActivityProperty } from "./ActivityProperty";
import { NodeTable } from "../graph/node-modal/NodeDetailsContent/NodeTable";

export type ToggleProcessActionModalData = {
    action: (processName: ProcessName, comment: string, nodeData: NodesDeploymentData) => Promise<unknown>;
    displayWarnings?: boolean;
};

function initialNodesData(params: ActivityNodeParameters[]) {
    return params.reduce(
        (paramObj, { nodeId, parameters }) => ({
            ...paramObj,
            [nodeId]: mapValues(parameters, (value) => value.defaultValue || ""),
        }),
        {},
    );
}

export function DeployProcessDialog(props: WindowContentProps<WindowKind, ToggleProcessActionModalData>): JSX.Element {
    // TODO: get rid of meta
    const {
        meta: { action, displayWarnings },
    } = props.data;
    const processName = useSelector(getProcessName);

    const activityParameters = useSelector(getActivityParameters);
    const activityNodeParameters = activityParameters["DEPLOY"] || ([] as ActivityNodeParameters[]);
    const initialValues = useMemo(() => initialNodesData(activityNodeParameters), [activityNodeParameters]);
    const [values, setValues] = useState(initialValues);

    const [comment, setComment] = useState("");
    const [validationError, setValidationError] = useState("");
    const featureSettings = useSelector(getFeatureSettings);
    const deploymentCommentSettings = featureSettings.deploymentCommentSettings;

    const dispatch = useDispatch();

    const confirmAction = useCallback(async () => {
        try {
            await action(processName, comment, values);
            props.close();
        } catch (error) {
            setValidationError(error?.response?.data);
        }
    }, [action, comment, dispatch, processName, props]);

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
                {activityNodeParameters.map((anp: ActivityNodeParameters) => (
                    <AdvancedParametersSection key={anp.nodeId} nodeId={anp.nodeId}>
                        <NodeTable>
                            {Object.entries(anp.parameters).map(([paramName, paramConfig]) => {
                                return (
                                    <ActivityProperty
                                        key={paramName}
                                        nodeName={anp.nodeId}
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

export default DeployProcessDialog;
