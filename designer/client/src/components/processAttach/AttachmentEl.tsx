import { DeleteOutline } from "@mui/icons-material";
import { Typography } from "@mui/material";
import React from "react";

import { StyledIconButton } from "../toolbars/activities/ActivitiesSearch";
import type { Attachment } from "./AddAttachment";
import { AttachmentDetails, AttachHeader } from "./StyledAttach";

export function AttachmentEl({
    attachment,
    handleDeleteAttachment,
    index,
}: {
    attachment: Attachment;
    handleDeleteAttachment: (index: number) => void;
    index: number;
}) {
    return (
        <li style={{ display: "flex" }}>
            <AttachmentDetails>
                <AttachHeader>
                    <Typography variant={"overline"} mr={1}>{` ${attachment.file.name} | v${attachment.processVersionId}`}</Typography>
                    <StyledIconButton color={"inherit"} onClick={() => handleDeleteAttachment(index)}>
                        <DeleteOutline fontSize={"small"} />
                    </StyledIconButton>
                </AttachHeader>
            </AttachmentDetails>
        </li>
    );
}
