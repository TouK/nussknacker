import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import { Box, styled } from "@mui/material";
import React from "react";

import { writeText } from "../../../common/ClipboardUtils";

const StyledCopyIconContainer = styled(Box)(({ theme }) => ({
    display: "none",
    position: "absolute",
    alignSelf: "flex-end",
    bottom: theme.spacing(-1.5),
    right: theme.spacing(0),
    padding: theme.spacing(0.5),
    cursor: "pointer",
}));

export interface Props {
    text: string;
}

export const CopyContent = ({ text }: Props) => {
    return (
        <StyledCopyIconContainer className="copyIcon" onClick={() => writeText(text)}>
            <ContentCopyIcon sx={{ fontSize: 18, color: "white" }} />
        </StyledCopyIconContainer>
    );
};
