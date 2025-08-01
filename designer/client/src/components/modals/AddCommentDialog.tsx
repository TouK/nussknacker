import { css, cx } from "@emotion/css";
import { Typography } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { getScenarioActivities } from "../../actions/nk/scenarioActivities";
import httpService from "../../http/HttpService";
import { getProcessName, getProcessVersionId } from "../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";
import { PromptContent } from "../../windowManager";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import CommentInput from "../comment/CommentInput";

const AddCommentDialog = (props: WindowContentProps) => {
    const [comment, setState] = useState("");
    const { t } = useTranslation();
    const processName = useAppSelector(getProcessName);
    const processVersionId = useAppSelector(getProcessVersionId);
    const dispatch = useAppDispatch();

    const confirmAction = useCallback(async () => {
        const { status } = await httpService.addComment(processName, processVersionId, comment);
        if (status === "success") {
            await dispatch(await getScenarioActivities(processName));
            props.close();
        }
    }, [comment, dispatch, processName, processVersionId, props]);

    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => props.close(), classname: LoadingButtonTypes.secondaryButton },
            { title: t("dialog.button.add", "Add"), action: () => confirmAction() },
        ],
        [confirmAction, props, t],
    );

    return (
        <PromptContent {...props} buttons={buttons}>
            <div className={cx("modalContentDark", css({ minWidth: 400 }))}>
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
};

export default AddCommentDialog;
