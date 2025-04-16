import { styled } from "@mui/material";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";

import HttpService from "../../../http/HttpService";
import { LoadingButton } from "../../../windowManager/LoadingButton";

const StyledLoadingButton = styled(LoadingButton)(() => ({
    fontSize: "12px",
    margin: 0,
}));

interface Props {
    nodeId: string;
}
export const GenerateNewEndpoint = ({ nodeId }: Props) => {
    const { t } = useTranslation();

    const handleSendHttpRequest = useCallback(async () => {
        try {
            await HttpService.generateNewEndpoint({ nodeId });
        } catch (error) {
            console.error("Error sending request:", error);
        }
    }, [nodeId]);

    return <StyledLoadingButton title={t("node.actions.generateNewEndpoint", "Generate new endpoint")} action={handleSendHttpRequest} />;
};
