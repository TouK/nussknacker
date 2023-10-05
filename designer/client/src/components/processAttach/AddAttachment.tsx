import React, { useCallback } from "react";
import Dropzone from "react-dropzone";
import { useTranslation } from "react-i18next";
import { addAttachment } from "../../actions/nk";
import { useDispatch, useSelector } from "react-redux";
import { getProcessId, getProcessVersionId } from "../../reducers/selectors/graph";
import ButtonUpload from "../../assets/img/icons/buttonUpload.svg";
import { NodeInput } from "../withFocus";
import { AddAttachmentsWrapper, AttachmentButton, AttachmentButtonText, AttachmentDropZone, AttachmentsContainer } from "./StyledAttach";

export function AddAttachment() {
    const { t } = useTranslation();
    const dispatch = useDispatch();
    const processId = useSelector(getProcessId);
    const processVersionId = useSelector(getProcessVersionId);

    const addFiles = useCallback(
        (files) => files.forEach((file) => dispatch(addAttachment(processId, processVersionId, file))),
        [dispatch, processId, processVersionId],
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
                            <AttachmentButtonText>
                                <span>{t("attachments.buttonText", "drop or choose a file")}</span>
                            </AttachmentButtonText>
                        </AttachmentDropZone>
                        <NodeInput {...getInputProps()} />
                    </AttachmentsContainer>
                )}
            </Dropzone>
        </AddAttachmentsWrapper>
    );
}
