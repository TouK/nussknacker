import { ContentCopy, Done } from "@mui/icons-material";
import React from "react";

import { InfoTooltip } from "../../components/graph/node-modal/editors/InfoTooltip";
import { StyledIconButton } from "./StyledIconButton";

interface Props {
    isCopied: boolean;
    onClick: () => void;
}
export const CopyIconButton = ({ isCopied, onClick }: Props) => {
    return (
        <InfoTooltip variant={"hover"} title={isCopied ? "Copied!" : "Copy"}>
            <StyledIconButton size="small" onClick={onClick} aria-label="copy" className={"copy-button"}>
                {isCopied ? <Done fontSize="small" /> : <ContentCopy fontSize="small" />}
            </StyledIconButton>
        </InfoTooltip>
    );
};
