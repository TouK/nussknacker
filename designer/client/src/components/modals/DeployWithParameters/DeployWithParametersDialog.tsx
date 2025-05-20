import { css, cx } from "@emotion/css";
import { FormHelperText, Typography } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { Suspense, useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import type { NodesDeploymentData, ScenarioSource} from "../../../http/HttpService";
import { ScenarioSourceType } from "../../../http/HttpService";
import { getGraph, getProcessName, getProcessVersionId, isSaveDisabled } from "../../../reducers/selectors/graph";
import { getFeatureSettings } from "../../../reducers/selectors/settings";
import type { WindowKind } from "../../../windowManager";
import { PromptContent } from "../../../windowManager";
import { LoadingButtonTypes } from "../../../windowManager/LoadingButton";
import CommentInput from "../../comment/CommentInput";
import { ErrorBoundary } from "../../common/error-boundary";
import { TextErrorBoundaryFallbackComponent } from "../../common/error-boundary";
import LoaderSpinner from "../../spinner/Spinner";
import { ScenarioActionResultType } from "../../toolbars/scenarioActions/buttons/types";
import type { ToggleProcessActionModalData } from "../DeployProcessDialog";
import ProcessDialogWarnings from "../ProcessDialogWarnings";
import { AdvancedParameters } from "./AdvancedParameters";

export function DeployWithParametersDialog(props: WindowContentProps<WindowKind, ToggleProcessActionModalData>): JSX.Element {
    // TODO: get rid of meta
    const {
        meta: { action, displayWarnings, actionName },
    } = props.data;
    const processName = useSelector(getProcessName);
    const processVersionId = useSelector(getProcessVersionId);
    const [parametersValues, setParametersValues] = useState<NodesDeploymentData>({});

    const [comment, setComment] = useState("");
    const [validationError, setValidationError] = useState("");
    const featureSettings = useSelector(getFeatureSettings);
    const deploymentCommentSettings = featureSettings.deploymentCommentSettings;

    const unsavedScenarioSource: ScenarioSource = {
        type: ScenarioSourceType.FROM_GRAPH,
        scenarioGraph: useSelector(getGraph)?.scenario?.scenarioGraph,
    };
    const savedScenarioSource: ScenarioSource = { type: ScenarioSourceType.LATEST_VERSION };
    const scenarioSource: ScenarioSource = useSelector(isSaveDisabled) ? savedScenarioSource : unsavedScenarioSource;

    const confirmAction = useCallback(async () => {
        const response = await action(processName, processVersionId, comment, parametersValues, scenarioSource);
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
    }, [action, comment, processName, props, processVersionId, parametersValues, scenarioSource]);

    const { t } = useTranslation();
    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => props.close(), classname: LoadingButtonTypes.secondaryButton },
            { title: t("dialog.button.ok", "Ok"), action: () => confirmAction() },
        ],
        [confirmAction, props, t],
    );

    return (
        <Suspense fallback={<LoaderSpinner show />}>
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
                    <ErrorBoundary
                        FallbackComponent={() => (
                            <TextErrorBoundaryFallbackComponent
                                header={t("error.textErrorBoundary.message", "There was a problem with loading advanced parameters")}
                                message={t(
                                    "error.textErrorBoundary.description",
                                    "You can still use this feature, but advanced parameters won’t be available. If the problem persists, please contact your system administrator.",
                                )}
                            />
                        )}
                    >
                        <AdvancedParameters
                            processName={processName}
                            actionName={actionName}
                            setParametersValues={setParametersValues}
                            parametersValues={parametersValues}
                        />
                    </ErrorBoundary>
                </div>
            </PromptContent>
        </Suspense>
    );
}

export default DeployWithParametersDialog;
