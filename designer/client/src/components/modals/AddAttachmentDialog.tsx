import React, { useCallback, useMemo, useState } from "react";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import { PromptContent } from "../../windowManager";
import { css, cx } from "@emotion/css";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { useTranslation } from "react-i18next";
import { Typography } from "@mui/material";
import httpService from "../../http/HttpService";
import { useSelector } from "react-redux";
import { getProcessName, getProcessVersionId } from "../../reducers/selectors/graph";
import { AddAttachment, Attachment } from "../processAttach/AddAttachment";
import { AttachmentEl } from "../processAttach/AttachmentEl";

export type AddAttachmentWindowContentProps = WindowContentProps<number, { handleSuccess?: () => Promise<void> }>;

const AddAttachmentDialog = (props: AddAttachmentWindowContentProps) => {
    const [attachments, setAttachment] = useState<Attachment[]>([]);
    const { t } = useTranslation();
    const processName = useSelector(getProcessName);
    const processVersionId = useSelector(getProcessVersionId);

    const confirmAction = useCallback(async () => {
        const attachmentPromises = attachments.map((attachment) =>
            httpService.addAttachment(processName, processVersionId, attachment.file),
        );
        const results = await Promise.all(attachmentPromises);

        if (results.every((result) => result === "success")) {
            props.close();
        }

        if (results.some((result) => result === "success")) {
            await props.data.meta?.handleSuccess();
        }
    }, [attachments, processName, processVersionId, props]);

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
