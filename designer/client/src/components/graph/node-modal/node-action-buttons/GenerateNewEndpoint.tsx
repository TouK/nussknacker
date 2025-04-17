import { styled } from "@mui/material";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import HttpService from "../../../../http/HttpService";
import { getProcessName } from "../../../../reducers/selectors/graph";
import type { NodeType } from "../../../../types";
import { LoadingButton } from "../../../../windowManager/LoadingButton";

const StyledLoadingButton = styled(LoadingButton)(({ theme }) => ({
    fontSize: "12px",
    textTransform: "inherit",
    padding: theme.spacing(0.5, 1),
    margin: 0,
}));

interface Props {
    node: NodeType;
}
export const GenerateNewEndpoint = ({ node }: Props) => {
    const scenarioName = useSelector(getProcessName);
    const { t } = useTranslation();

    const handleSendHttpRequest = useCallback(async () => {
        try {
            await HttpService.nodeActions(scenarioName, "generate-endpoint", node);
        } catch (error) {
            console.error("Error sending request:", error);
        }
    }, [node, scenarioName]);

    return <StyledLoadingButton title={t("node.actions.generateNewEndpoint", "Generate New Endpoint")} action={handleSendHttpRequest} />;
};
