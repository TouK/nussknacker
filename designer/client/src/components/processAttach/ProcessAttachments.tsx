import React from "react";
import { useSelector } from "react-redux";
import { RootState } from "../../reducers";
import { getCapabilities } from "../../reducers/selectors/other";
import { AttachmentEl } from "./AttachmentEl";
import { AddAttachment } from "./AddAttachment";
import { ProcessAttachmentsList, ProcessAttachmentsStyled } from "./StyledAttach";

export function ProcessAttachments() {
    const { write } = useSelector(getCapabilities);
    const attachments = useSelector((s: RootState) => s.processActivity.attachments);

    return (
        <ProcessAttachmentsStyled>
            <ProcessAttachmentsList>
                {attachments.map((a) => (
                    <AttachmentEl key={a.id} data={a} />
                ))}
            </ProcessAttachmentsList>
            {write && <AddAttachment />}
        </ProcessAttachmentsStyled>
    );
}

export default ProcessAttachments;
