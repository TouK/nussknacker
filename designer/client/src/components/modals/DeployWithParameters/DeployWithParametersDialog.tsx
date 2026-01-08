import { css, cx } from "@emotion/css";
import { FormHelperText, Typography } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { Suspense, useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import type { NodesDeploymentData } from "../../../http/HttpService/types";
import { getProcessName } from "../../../reducers/selectors/graph";
import { getFeatureSettings } from "../../../reducers/selectors/settings";
import { useAppSelector } from "../../../store/storeHelpers";
import { LoadingButtonTypes } from "../../../windowManager/LoadingButton";
import { PromptContent } from "../../../windowManager/PromptContent";
import type { WindowKind } from "../../../windowManager/WindowKind";
import CommentInput from "../../comment/CommentInput";
import { ErrorBoundary } from "../../common/error-boundary/ErrorBoundary";
import { TextErrorBoundaryFallbackComponent } from "../../common/error-boundary/fallbackComponent/TextErrorBoundaryFallbackComponent";
import LoaderSpinner from "../../spinner/Spinner";
import { ScenarioActionResultType } from "../../toolbars/scenarioActions/buttons/types";
import type { ToggleProcessActionModalData } from "../DeployProcessDialog";
import ProcessDialogWarnings from "../ProcessDialogWarnings";
import { AdvancedParameters } from "./AdvancedParameters";

export function DeployWithParametersDialog(props: WindowContentProps<WindowKind, ToggleProcessActionModalData>): React.JSX.Element {
    // TODO: get rid of meta
    const {
        meta: { action, displayWarnings, actionName },
    } = props.data;
    const processName = useAppSelector(getProcessName);
    const [parametersValues, setParametersValues] = useState<NodesDeploymentData>({});

    const [comment, setComment] = useState("");
    const [validationError, setValidationError] = useState("");
    const featureSettings = useAppSelector(getFeatureSettings);
    const deploymentCommentSettings = featureSettings.deploymentCommentSettings;

    const confirmAction = useCallback(async () => {
        const response = await action(comment, parametersValues);
        switch (response.scenarioActionResultType) {
            case ScenarioActionResultType.DeploySuccess:
            case ScenarioActionResultType.Success:
            case ScenarioActionResultType.UnhandledError:
                props.close();
                break;
            case ScenarioActionResultType.ValidationError:
                setValidationError(response.msg);
                break;
        }
    }, [action, comment, props, parametersValues]);

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
