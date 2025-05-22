import { css, cx } from "@emotion/css";
import { FormHelperText, Typography } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import type { NodesDeploymentData, ScenarioSource } from "../../http/HttpService";
import { ScenarioSourceType } from "../../http/HttpService";
import { getGraph, getProcessName, getProcessVersionId, getScenarioLabels, isSaveDisabled } from "../../reducers/selectors/graph";
import { getFeatureSettings } from "../../reducers/selectors/settings";
import type { WindowKind } from "../../windowManager";
import { PromptContent } from "../../windowManager";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import CommentInput from "../comment/CommentInput";
import type { ProcessName, ProcessVersionId } from "../Process/types";
import type { ScenarioActionResult } from "../toolbars/scenarioActions/buttons/types";
import { ScenarioActionResultType } from "../toolbars/scenarioActions/buttons/types";
import ProcessDialogWarnings from "./ProcessDialogWarnings";

export type ToggleProcessActionModalData = {
    action: (
        processName: ProcessName,
        processVersionId: ProcessVersionId,
        comment: string,
        nodeData?: NodesDeploymentData,
        scenarioSource?: ScenarioSource,
    ) => Promise<ScenarioActionResult>;
    displayWarnings?: boolean;
    actionName?: string;
};

export function DeployProcessDialog(props: WindowContentProps<WindowKind, ToggleProcessActionModalData>): JSX.Element {
    // TODO: get rid of meta
    const {
        meta: { action, displayWarnings },
    } = props.data;
    const processName = useSelector(getProcessName);
    const processVersionId = useSelector(getProcessVersionId);
    const [comment, setComment] = useState("");
    const [validationError, setValidationError] = useState("");
    const featureSettings = useSelector(getFeatureSettings);
    const deploymentCommentSettings = featureSettings.deploymentCommentSettings;
    const unsavedScenarioSource: ScenarioSource = {
        type: ScenarioSourceType.FROM_GRAPH,
        scenarioGraph: useSelector(getGraph)?.scenario?.scenarioGraph,
        scenarioLabels: useSelector(getScenarioLabels),
    };
    const savedScenarioSource: ScenarioSource = { type: ScenarioSourceType.LATEST_VERSION };
    const scenarioSource: ScenarioSource = useSelector(isSaveDisabled) ? savedScenarioSource : unsavedScenarioSource;

    const confirmAction = useCallback(async () => {
        const response = await action(processName, processVersionId, comment, null, scenarioSource);
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
    }, [action, comment, processName, props, processVersionId, scenarioSource]);

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
            </div>
        </PromptContent>
    );
}

export default DeployProcessDialog;
