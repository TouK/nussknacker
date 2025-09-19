import { css, cx } from "@emotion/css";
import { FormHelperText, Typography } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { getScenarioActivities } from "../../actions/nk/scenarioActivities";
import httpService from "../../http/instance";
import { getProcessName } from "../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { PromptContent } from "../../windowManager/PromptContent";
import CommentInput from "../comment/CommentInput";
import type { ModifyActivityCommentMeta } from "../toolbars/activities/types";

const ModifyActivityCommentDialog = (props: WindowContentProps<number, ModifyActivityCommentMeta>) => {
    const meta = props.data.meta;
    const [comment, setState] = useState(meta.existingComment);
    const { t } = useTranslation();
    const processName = useAppSelector(getProcessName);
    const dispatch = useAppDispatch();
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
            { title: meta.confirmButtonText, action: () => confirmAction() },
        ],
        [confirmAction, meta.confirmButtonText, props, t],
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

export default ModifyActivityCommentDialog;
