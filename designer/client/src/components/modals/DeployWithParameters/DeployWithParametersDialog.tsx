import { css, cx } from "@emotion/css";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import { getProcessName, getProcessVersionId } from "../../../reducers/selectors/graph";
import { getFeatureSettings } from "../../../reducers/selectors/settings";
import { PromptContent, WindowKind } from "../../../windowManager";
import CommentInput from "../../comment/CommentInput";
import ProcessDialogWarnings from "../ProcessDialogWarnings";
import { FormHelperText, Typography } from "@mui/material";
import { LoadingButtonTypes } from "../../../windowManager/LoadingButton";
import { ScenarioActionResultType } from "../../toolbars/scenarioActions/buttons/types";
import { NodesDeploymentData } from "../../../http/HttpService";
import { ToggleProcessActionModalData } from "../DeployProcessDialog";
import { useLocalstorageState } from "rooks";
import { AdvancedParameters } from "./AdvancedParameters";
import { ErrorBoundary } from "../../common/error-boundary";
import { TextErrorBoundaryFallbackComponent } from "../../common/error-boundary";

export function DeployWithParametersDialog(props: WindowContentProps<WindowKind, ToggleProcessActionModalData>): JSX.Element {
    // TODO: get rid of meta
    const {
        meta: { action, displayWarnings },
    } = props.data;
    const processName = useSelector(getProcessName);
    const processVersionId = useSelector(getProcessVersionId);
    const [parametersValues, setParametersValues] = useState<NodesDeploymentData>({});

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

    const [expandedState, setExpandedState] = useLocalstorageState("actionParametersExpandedState", {});

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
                        expandedState={expandedState}
                        setExpandedState={setExpandedState}
                        setParametersValues={setParametersValues}
                        parametersValues={parametersValues}
                    />
                </ErrorBoundary>
            </div>
        </PromptContent>
    );
}

export default DeployWithParametersDialog;
