import { css, cx } from "@emotion/css";
import { FormHelperText, Typography } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import type { NodesDeploymentData, ScenarioGraphSource } from "../../http/HttpService/types";
import { getFeatureSettings } from "../../reducers/selectors/settings";
import { useAppSelector } from "../../store/storeHelpers";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { PromptContent } from "../../windowManager/PromptContent";
import type { WindowKind } from "../../windowManager/WindowKind";
import CommentInput from "../comment/CommentInput";
import type { ScenarioActionResult } from "../toolbars/scenarioActions/buttons/types";
import { ScenarioActionResultType } from "../toolbars/scenarioActions/buttons/types";
import ProcessDialogWarnings from "./ProcessDialogWarnings";

export type ToggleProcessActionModalData = {
    action: (comment: string, nodeData?: NodesDeploymentData, scenarioSource?: ScenarioGraphSource) => Promise<ScenarioActionResult>;
    displayWarnings?: boolean;
    actionName?: string;
};

export function DeployProcessDialog(props: WindowContentProps<WindowKind, ToggleProcessActionModalData>): React.JSX.Element {
    // TODO: get rid of meta
    const {
        meta: { action, displayWarnings },
    } = props.data;
    const [comment, setComment] = useState("");
    const [validationError, setValidationError] = useState("");
    const featureSettings = useAppSelector(getFeatureSettings);
    const deploymentCommentSettings = featureSettings.deploymentCommentSettings;

    const confirmAction = useCallback(async () => {
        const response = await action(comment);
        switch (response.scenarioActionResultType) {
            case ScenarioActionResultType.Success:
            case ScenarioActionResultType.DeploySuccess:
            case ScenarioActionResultType.UnhandledError:
                props.close();
                break;
            case ScenarioActionResultType.ValidationError:
                setValidationError(response.msg);
                break;
        }
    }, [action, comment, props]);

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
