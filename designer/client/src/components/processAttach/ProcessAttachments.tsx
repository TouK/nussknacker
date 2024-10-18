import React from "react";
import { useSelector } from "react-redux";
import { RootState } from "../../reducers";
import { getCapabilities } from "../../reducers/selectors/other";
import { AttachmentEl } from "./AttachmentEl_deprecated";
import { AddAttachment } from "./AddAttachment_deprecated";
import { ProcessAttachmentsList, ProcessAttachmentsStyled } from "./StyledAttach";
import { getProcessName } from "../../reducers/selectors/graph";

export function ProcessAttachments() {
    const { write } = useSelector(getCapabilities);
    const processName = useSelector(getProcessName);
    const attachments = useSelector((s: RootState) => s.processActivity.attachments);

    return (
        <ProcessAttachmentsStyled>
            <ProcessAttachmentsList>
                {attachments.map((a) => (
                    <AttachmentEl key={a.id} data={a} processName={processName} />
                ))}
            </ProcessAttachmentsList>
            {write && <AddAttachment />}
        </ProcessAttachmentsStyled>
    );
}

export default ProcessAttachments;
