import { Typography } from "@mui/material";
import React, { useCallback } from "react";
import Dropzone from "react-dropzone";
import { useTranslation } from "react-i18next";

import ButtonUpload from "../../assets/img/icons/buttonUpload.svg";
import { getProcessName, getProcessVersionId } from "../../reducers/selectors/graph";
import { useAppSelector } from "../../store/storeHelpers";
import { NodeInput } from "../FormElements";
import { AddAttachmentsWrapper, AttachmentButton, AttachmentDropZone, AttachmentsContainer } from "./StyledAttach";

export interface Attachment {
    processName: string;
    processVersionId: number;
    file: File;
}

interface Props {
    handleSetAttachment: ({ processName, processVersionId, file }: Attachment) => void;
}

export function AddAttachment({ handleSetAttachment }: Props) {
    const { t } = useTranslation();
    const processName = useAppSelector(getProcessName);
    const processVersionId = useAppSelector(getProcessVersionId);

    const addFiles = useCallback(
        (files: File[]) => files.forEach((file) => handleSetAttachment({ processName, processVersionId, file })),
        [handleSetAttachment, processName, processVersionId],
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
