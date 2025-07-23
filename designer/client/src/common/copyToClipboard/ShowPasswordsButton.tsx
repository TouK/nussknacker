import { Visibility, VisibilityOff } from "@mui/icons-material";
import React from "react";

import { InfoTooltip } from "../../components/graph/node-modal/editors/InfoTooltip";
import { StyledIconButton } from "./CopyIconButton";

type Props = {
    visible: boolean;
    onClick: () => void;
};

export const ShowPasswordsButton = ({ onClick, visible }: Props) => {
    return (
        <InfoTooltip variant={"hover"} title={"Reveal sanitized text"}>
            <StyledIconButton size="small" onClick={onClick} aria-label="reveal" className={"reveal-button"}>
                {visible ? <Visibility fontSize="small" /> : <VisibilityOff fontSize="small" />}
            </StyledIconButton>
        </InfoTooltip>
    );
};
