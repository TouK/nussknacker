import React from "react";
import { AttachmentDetails, AttachHeader } from "./StyledAttach";
import { Typography } from "@mui/material";
import { Attachment } from "./AddAttachment";
import { DeleteOutline } from "@mui/icons-material";
import { StyledIconButton } from "../toolbars/activities/ActivitiesSearch";

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
