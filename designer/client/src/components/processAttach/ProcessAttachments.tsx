import React from "react";
import { useSelector } from "react-redux";
import { RootState } from "../../reducers";
import { getCapabilities } from "../../reducers/selectors/other";
import { AttachmentEl } from "./AttachmentEl";
import { AddAttachment } from "./AddAttachment";
import { ProcessAttachmentsList, ProcessAttachmentsStyled } from "./StyledAttach";
import { getScenarioName } from "../../reducers/selectors/graph";

export function ProcessAttachments() {
    const { write } = useSelector(getCapabilities);
    const processName = useSelector(getScenarioName);
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
