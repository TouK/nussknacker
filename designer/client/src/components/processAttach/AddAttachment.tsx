import React, { useCallback } from "react";
import Dropzone from "react-dropzone";
import { useTranslation } from "react-i18next";
import { addAttachment } from "../../actions/nk";
import { useDispatch, useSelector } from "react-redux";
import { getScenarioName, getScenarioVersionId } from "../../reducers/selectors/graph";
import ButtonUpload from "../../assets/img/icons/buttonUpload.svg";
import { NodeInput } from "../withFocus";
import { AddAttachmentsWrapper, AttachmentButton, AttachmentButtonText, AttachmentDropZone, AttachmentsContainer } from "./StyledAttach";

export function AddAttachment() {
    const { t } = useTranslation();
    const dispatch = useDispatch();
    const processName = useSelector(getScenarioName);
    const processVersionId = useSelector(getScenarioVersionId);

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
