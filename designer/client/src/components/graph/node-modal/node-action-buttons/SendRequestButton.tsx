import { Box } from "@mui/material";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";

import HttpService from "../../../../http/HttpService/instance";
import { getProcessName } from "../../../../reducers/selectors/graph";
import { useAppSelector } from "../../../../store/storeHelpers";
import type { NodeType } from "../../../../types/node";
import { InfoTooltip } from "../editors/InfoTooltip/InfoTooltip";
import { StyledLoadingButton } from "./StyledLoadingButton";

interface Props {
    disabled: boolean;
    node: NodeType;
    infoTooltip?: string | undefined;
}
export const SendRequestButton = ({ disabled, node, infoTooltip }: Props) => {
    const scenarioName = useAppSelector(getProcessName);
    const { t } = useTranslation();

    const handleSendHttpRequest = useCallback(async () => {
        try {
            await HttpService.nodeActions(scenarioName, "send-sample-request", node);
        } catch (error) {
            console.error("Error sending request:", error);
        }
    }, [node, scenarioName]);

    return (
        <Box display={"flex"} alignItems={"flex-end"} flexDirection={"column"} width={"100%"}>
            <Box display={"flex"} justifyContent={"center"} alignItems={"center"} gap={0.5}>
                <StyledLoadingButton
                    disabled={disabled}
                    title={t("node.actions.sendRequest.button.title", "Send Request")}
                    action={handleSendHttpRequest}
                />
                {infoTooltip && <InfoTooltip title={infoTooltip} variant={"hover"} />}
            </Box>
        </Box>
    );
};
