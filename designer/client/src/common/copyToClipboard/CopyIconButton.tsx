import { ContentCopy, Done } from "@mui/icons-material";
import { IconButton, styled } from "@mui/material";
import React from "react";

import { InfoTooltip } from "../../components/graph/node-modal/editors/InfoTooltip";

const StyledCopyIconButton = styled(IconButton)(({ theme }) => ({
    color: theme.palette.common.white,
    padding: theme.spacing(0.5),
    "&:hover": {
        backgroundColor: theme.palette.action.hover,
    },
}));

interface Props {
    isCopied: boolean;
    onClick: () => void;
}
export const CopyIconButton = ({ isCopied, onClick }: Props) => {
    return (
        <InfoTooltip variant={"hover"} title={isCopied ? "Copied!" : "Copy code"}>
            <StyledCopyIconButton size="small" onClick={onClick} aria-label="copy code" className={"copy-button"}>
                {isCopied ? <Done fontSize="small" /> : <ContentCopy fontSize="small" />}
            </StyledCopyIconButton>
        </InfoTooltip>
    );
};
