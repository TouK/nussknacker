import { EditOutlined } from "@mui/icons-material";
import React from "react";

import { StyledButton } from "../../../../../styledButton";
import { InfoTooltip } from "../../../../editors/InfoTooltip/InfoTooltip";

type Props = {
    isEditing: boolean;
    onStartEditing: () => void;
};

export const EditButton = ({ isEditing, onStartEditing }: Props) => (
    <InfoTooltip title={"Edit name"} variant={"hover"} enterDelay={500}>
        <StyledButton
            data-testid="edit-test-case-name"
            onClick={onStartEditing}
            disabled={isEditing}
            sx={{ display: "flex", justifyContent: "center", alignItems: "center" }}
        >
            <EditOutlined fontSize="small" />
        </StyledButton>
    </InfoTooltip>
);
