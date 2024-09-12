import { css, cx } from "@emotion/css";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch } from "react-redux";
import { displayCurrentProcessVersion, loadProcessToolbarsConfiguration } from "../../actions/nk";
import { PromptContent } from "../../windowManager";
import { ThunkAction } from "../../actions/reduxTypes";
import {
    getScenarioGraph,
    getProcessName,
    getProcessUnsavedNewName,
    isProcessRenamed,
    getScenarioLabels,
} from "../../reducers/selectors/graph";
import HttpService from "../../http/HttpService";
import { ActionCreators as UndoActionCreators } from "redux-undo";
import { visualizationUrl } from "../../common/VisualizationUrl";
import { useLocation, useNavigate } from "react-router-dom";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { getScenarioActivities } from "../../actions/nk/scenarioActivities";
import { ActivityCommentTextField } from "./ActivityCommentTextField";
import { ActivityHeader } from "./ActivityHeader";

export function SaveProcessDialog(props: WindowContentProps): JSX.Element {
    const location = useLocation();
    const navigate = useNavigate();
    const saveProcess = useCallback(
        (comment: string): ThunkAction => {
            return async (dispatch, getState) => {
                const state = getState();
                const scenarioGraph = getScenarioGraph(state);
                const currentProcessName = getProcessName(state);
                const labels = getScenarioLabels(state);

                // save changes before rename and force same processName everywhere
                await HttpService.saveProcess(currentProcessName, scenarioGraph, comment, labels);

                const unsavedNewName = getProcessUnsavedNewName(state);
                const isRenamed = isProcessRenamed(state) && (await HttpService.changeProcessName(currentProcessName, unsavedNewName));
                const processName = isRenamed ? unsavedNewName : currentProcessName;

                await dispatch(UndoActionCreators.clearHistory());
                await dispatch(displayCurrentProcessVersion(processName));
                await dispatch(await getScenarioActivities(processName));

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
            { title: t("dialog.button.ok", "Apply"), action: () => confirmAction() },
        ],
        [confirmAction, props, t],
    );

    return (
        <PromptContent {...props} buttons={buttons}>
            <div className={cx("modalContentDark", css({ minWidth: 600 }))}>
                <ActivityHeader title={props.data.title} />
                <ActivityCommentTextField onChange={(e) => setState(e.target.value)} autoFocus />
            </div>
        </PromptContent>
    );
}

export default SaveProcessDialog;
