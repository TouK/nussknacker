import { css, cx } from "@emotion/css";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch } from "react-redux";
import { displayCurrentProcessVersion, displayProcessActivity, loadProcessToolbarsConfiguration } from "../../actions/nk";
import { PromptContent } from "../../windowManager";
import { CommentInput } from "../comment/CommentInput";
import { ThunkAction } from "../../actions/reduxTypes";
import { getScenarioGraph, getProcessName, getProcessUnsavedNewName, isProcessRenamed } from "../../reducers/selectors/graph";
import HttpService from "../../http/HttpService";
import { ActionCreators as UndoActionCreators } from "redux-undo";
import { visualizationUrl } from "../../common/VisualizationUrl";
import { useLocation, useNavigate } from "react-router-dom";
import { Typography } from "@mui/material";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";

export function SaveProcessDialog(props: WindowContentProps): JSX.Element {
    const location = useLocation();
    const navigate = useNavigate();
    const saveProcess = useCallback(
        (comment: string): ThunkAction => {
            return async (dispatch, getState) => {
                const state = getState();
                const scenarioGraph = getScenarioGraph(state);
                const currentProcessName = getProcessName(state);

                // save changes before rename and force same processName everywhere
                await HttpService.saveProcess(currentProcessName, scenarioGraph, comment);

                const unsavedNewName = getProcessUnsavedNewName(state);
                const isRenamed = isProcessRenamed(state) && (await HttpService.changeProcessName(currentProcessName, unsavedNewName));
                const processName = isRenamed ? unsavedNewName : currentProcessName;

                await dispatch(UndoActionCreators.clearHistory());
                await dispatch(displayCurrentProcessVersion(processName));
                await dispatch(displayProcessActivity(processName));

                if (isRenamed) {
                    await dispatch(loadProcessToolbarsConfiguration(unsavedNewName));
                    navigate(
                        {
                            ...location,
                            pathname: location.pathname.replace(visualizationUrl(currentProcessName), visualizationUrl(unsavedNewName)),
                        },
                        { replace: true },
                    );
                }
            };
        },
        [location, navigate],
    );

    const [comment, setState] = useState("");
    const dispatch = useDispatch();

    const confirmAction = useCallback(async () => {
        await dispatch(saveProcess(comment));
        props.close();
    }, [comment, dispatch, props, saveProcess]);

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
            <div className={cx("modalContentDark", css({ minWidth: 600 }))}>
                <Typography variant={"h3"}>{props.data.title}</Typography>
                <CommentInput
                    onChange={(e) => setState(e.target.value)}
                    value={comment}
                    className={css({
                        minWidth: 600,
                        minHeight: 80,
                    })}
                    autoFocus
                />
            </div>
        </PromptContent>
    );
}

export default SaveProcessDialog;
