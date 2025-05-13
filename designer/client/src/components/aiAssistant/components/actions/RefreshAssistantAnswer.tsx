import { useMessageRuntime } from "@assistant-ui/react";
import RefreshIcon from "@mui/icons-material/Refresh";
import { styled, Tooltip } from "@mui/material";
import React, { useCallback } from "react";

const StyledRefreshIcon = styled(RefreshIcon)(() => ({
    cursor: "pointer",
    fontSize: 18,
    color: "white",
}));

export const RefreshAssistantAnswer = () => {
    const { reload } = useMessageRuntime();

    const handleSend = useCallback(() => {
        reload();
    }, [reload]);

    return (
        <Tooltip title="Refresh">
            <StyledRefreshIcon onClick={handleSend} />
        </Tooltip>
    );
};
