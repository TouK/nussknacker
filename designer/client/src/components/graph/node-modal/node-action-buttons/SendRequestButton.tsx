import { Alert, Box, Collapse } from "@mui/material";
import React, { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";

import { setTestData } from "../../../../actions/nk/displayTestResults";
import HttpService from "../../../../http/HttpService";
import { getProcessName } from "../../../../reducers/selectors/graph";
import type { NodeType } from "../../../../types";
import { useSourceParameters } from "../../../modals/AdhocTesting/useAdhocTestingAction";
import { InfoTooltip } from "../editors/InfoTooltip";
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
    const [showInfoAfterSendData, setShowInfoAfterSendData] = useState<boolean>(false);
    const scenarioName = useSelector(getProcessName);
    const dispatch = useDispatch();
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
            setShowInfoAfterSendData(true);
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

            <Collapse sx={{ width: "80%" }} in={showInfoAfterSendData} timeout="auto">
                <Alert
                    icon={false}
                    sx={{ width: "100%" }}
                    severity="success"
                    onClose={() => {
                        setShowInfoAfterSendData(false);
                    }}
                >
                    <MarkdownStyled>
                        {t(
                            "node.actions.sendRequest.successMessage",
                            `
The message has been sent. You can now check the processing outcome by:
- Reviewing the metrics,
- Using counts,
- Using the logging component to inspect the message content.
`,
                        )}
                    </MarkdownStyled>
                </Alert>
            </Collapse>
        </Box>
    );
};
