import React, { useCallback, useMemo, useState } from "react";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import { PromptContent } from "../../windowManager";
import { css, cx } from "@emotion/css";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { useTranslation } from "react-i18next";
import CommentInput from "../comment/CommentInput";
import { FormHelperText, Typography } from "@mui/material";
import httpService from "../../http/HttpService";
import { useDispatch, useSelector } from "react-redux";
import { getProcessName } from "../../reducers/selectors/graph";
import { getScenarioActivities } from "../../actions/nk/scenarioActivities";
import { ModifyActivityCommentMeta } from "../toolbars/activities/types";

const ModifyExistingCommentDialog = (props: WindowContentProps<number, ModifyActivityCommentMeta>) => {
    const meta = props.data.meta;
    const [comment, setState] = useState(meta.existingComment);
    const { t } = useTranslation();
    const processName = useSelector(getProcessName);
    const dispatch = useDispatch();
    const [validationError, setValidationError] = useState("");

    const confirmAction = useCallback(async () => {
        const response = await httpService.updateComment(processName, comment, meta.scenarioActivityId);
        if (response.status === "success") {
            await dispatch(await getScenarioActivities(processName));
            props.close();
        }

        if (response.status === "error") {
            setValidationError(response.error.response.data);
        }
    }, [comment, dispatch, meta.scenarioActivityId, processName, props]);

    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => props.close(), classname: LoadingButtonTypes.secondaryButton },
            { title: t("dialog.button.modify", "Modify"), action: () => confirmAction() },
        ],
        [confirmAction, props, t],
    );

    return (
        <PromptContent {...props} buttons={buttons}>
            <div className={cx("modalContentDark", css({ minWidth: 400 }))}>
                <Typography variant={"h3"}>{props.data.title}</Typography>
                <CommentInput
                    defaultValue={meta.placeholder}
                    onChange={(e) => setState(e.target.value)}
                    value={comment}
                    className={css({
                        minWidth: 600,
                        minHeight: 80,
                    })}
                    autoFocus
                    onFocus={(event) => {
                        event.target.setSelectionRange(event.target.value.length, event.target.value.length);
                    }}
                />
                <FormHelperText title={validationError} error>
                    {validationError}
                </FormHelperText>
            </div>
        </PromptContent>
    );
};

export default ModifyExistingCommentDialog;
