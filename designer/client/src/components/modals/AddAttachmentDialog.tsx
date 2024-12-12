import React, { useCallback, useMemo, useState } from "react";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import { PromptContent } from "../../windowManager";
import { css, cx } from "@emotion/css";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { useTranslation } from "react-i18next";
import { Typography } from "@mui/material";
import httpService from "../../http/HttpService";
import { useDispatch, useSelector } from "react-redux";
import { getProcessVersionId, getProcessName } from "../../reducers/selectors/graph";
import { AddAttachment, Attachment } from "../processAttach/AddAttachment";
import { AttachmentEl } from "../processAttach/AttachmentEl";
import { getScenarioActivities } from "../../actions/nk/scenarioActivities";

const AddAttachmentDialog = (props: WindowContentProps) => {
    const [attachments, setAttachment] = useState<Attachment[]>([]);
    const { t } = useTranslation();
    const processName = useSelector(getProcessName);
    const processVersionId = useSelector(getProcessVersionId);
    const dispatch = useDispatch();

    const confirmAction = useCallback(async () => {
        const attachmentPromises = attachments.map((attachment) =>
            httpService.addAttachment(processName, processVersionId, attachment.file),
        );
        const results = await Promise.all(attachmentPromises);

        if (results.every(({ status }) => status === "success")) {
            props.close();
        }

        if (results.some(({ status }) => status === "success")) {
            await dispatch(await getScenarioActivities(processName));
        }
    }, [attachments, dispatch, processName, processVersionId, props]);

    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => props.close(), classname: LoadingButtonTypes.secondaryButton },
            { title: t("dialog.button.add", "Add"), action: () => confirmAction() },
        ],
        [confirmAction, props, t],
    );

    const handleSetAttachment = useCallback((attachment: Attachment) => {
        setAttachment((prevState) => [...prevState, attachment]);
    }, []);

    const handleDeleteAttachment = useCallback((attachmentIndex: number) => {
        setAttachment((prevState) => prevState.filter((_, index) => index !== attachmentIndex));
    }, []);

    return (
        <PromptContent {...props} buttons={buttons}>
            <div className={cx("modalContentDark", css({ minWidth: 400 }))}>
                <Typography variant={"h3"}>{props.data.title}</Typography>
                <AddAttachment handleSetAttachment={handleSetAttachment} />
                {attachments.map((attachment, index) => (
                    <AttachmentEl key={index} attachment={attachment} index={index} handleDeleteAttachment={handleDeleteAttachment} />
                ))}
            </div>
        </PromptContent>
    );
};

export default AddAttachmentDialog;
