import React, { useCallback } from "react";
import Dropzone from "react-dropzone";
import { useTranslation } from "react-i18next";
import { addAttachment } from "../../actions/nk";
import { useDispatch, useSelector } from "react-redux";
import { getProcessName, getProcessVersionId } from "../../reducers/selectors/graph";
import ButtonUpload from "../../assets/img/icons/buttonUpload.svg";
import { NodeInput } from "../FormElements";
import { AddAttachmentsWrapper, AttachmentButton, AttachmentDropZone, AttachmentsContainer } from "./StyledAttach";
import { Typography } from "@mui/material";

export function AddAttachment() {
    const { t } = useTranslation();
    const dispatch = useDispatch();
    const processName = useSelector(getProcessName);
    const processVersionId = useSelector(getProcessVersionId);

    const addFiles = useCallback(
        (files) => files.forEach((file) => dispatch(addAttachment(processName, processVersionId, file))),
        [dispatch, processName, processVersionId],
    );

    return (
        <AddAttachmentsWrapper>
            <Dropzone onDrop={addFiles}>
                {({ getRootProps, getInputProps }) => (
                    <AttachmentsContainer {...getRootProps()}>
                        <AttachmentDropZone>
                            <AttachmentButton>
                                <ButtonUpload />
                            </AttachmentButton>
                            <Typography variant={"caption"}>{t("attachments.buttonText", "drop or choose a file")}</Typography>
                        </AttachmentDropZone>
                        <NodeInput {...getInputProps()} />
                    </AttachmentsContainer>
                )}
            </Dropzone>
        </AddAttachmentsWrapper>
    );
}
