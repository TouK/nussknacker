import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import { styled, Tooltip } from "@mui/material";
import React from "react";

import { writeText } from "../../../../common/ClipboardUtils";

const StyledCopyIcon = styled(ContentCopyIcon)(() => ({
    cursor: "pointer",
    fontSize: 18,
    color: "white",
}));

export interface Props {
    text: string;
}

export const CopyContent = ({ text }: Props) => {
    return (
        <Tooltip title="Copy">
            <StyledCopyIcon onClick={() => writeText(text)} />
        </Tooltip>
    );
};
