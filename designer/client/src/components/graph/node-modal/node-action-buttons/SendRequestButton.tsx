import { Box } from "@mui/material";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";

import { setTestData } from "../../../../actions/nk/displayTestResults";
import HttpService from "../../../../http/HttpService/instance";
import { getProcessName } from "../../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import type { NodeType } from "../../../../types/node";
import { useSourceParameters } from "../../../modals/AdhocTesting/useAdhocTestingAction";
import { InfoTooltip } from "../editors/InfoTooltip/InfoTooltip";
import { MarkdownStyled } from "../MarkdownStyled";
import { StyledLoadingButton } from "./StyledLoadingButton";

interface Props {
    disabled: boolean;
    node: NodeType;
    expression: string;
    infoTooltip?: string | undefined;
}
export const SendRequestButton = ({ disabled, node, expression, infoTooltip }: Props) => {
    const { sourceId, sourceParameters } = useSourceParameters();
    const scenarioName = useAppSelector(getProcessName);
    const dispatch = useAppDispatch();
    const { t } = useTranslation();

    const handleSendHttpRequest = useCallback(async () => {
        try {
            await HttpService.nodeActions(scenarioName, "send-sample-request", node);

            // We assume that the source has only a single Data sample parameter
            const sourceParameter = sourceParameters?.[node.id]?.parameters?.[0];
            if (sourceParameter) {
                dispatch(
                    setTestData({
                        sourceId,
                        parameterExpressions: { [sourceParameter.name]: { expression, language: sourceParameter.defaultValue.language } },
                    }),
                );
            }
        } catch (error) {
            console.error("Error sending request:", error);
        }
    }, [dispatch, expression, node, scenarioName, sourceId, sourceParameters]);

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
