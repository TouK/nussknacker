import { Box, Typography } from "@mui/material";
import React, { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";

import HttpService from "../../../../http/HttpService/instance";
import { getProcessName } from "../../../../reducers/selectors/graph";
import { useAppSelector } from "../../../../store/storeHelpers";
import type { NodeType } from "../../../../types/node";
import { StyledLoadingButton } from "./StyledLoadingButton";

interface TestResult {
    ok: boolean;
    message: string;
    firstMsg?: string;
}

interface Props {
    disabled: boolean;
    node: NodeType;
}

export const TestConnectionButton = ({ disabled, node }: Props) => {
    const scenarioName = useAppSelector(getProcessName);
    const { t } = useTranslation();
    const [result, setResult] = useState<TestResult | null>(null);

    const handleTestConnection = useCallback(async () => {
        setResult(null);
        try {
            const response = await HttpService.nodeActions(scenarioName, "test-ws-forwarder", node);
            if (response.result?.actionName === "WS_FORWARDER_TEST_RESULT") {
                setResult({
                    ok: response.result.ok,
                    message: response.result.message,
                    firstMsg: response.result.firstMsg,
                });
            }
        } catch (error) {
            setResult({ ok: false, message: String(error) });
        }
    }, [node, scenarioName]);

    return (
        <Box display={"flex"} flexDirection={"column"} alignItems={"flex-end"} width={"100%"}>
            <StyledLoadingButton
                disabled={disabled}
                title={t("node.actions.testConnection.buttonTitle", "Test Connection")}
                action={handleTestConnection}
            />
            {result && (
                <Box
                    sx={(theme) => ({
                        mt: 0.5,
                        p: 1,
                        width: "100%",
                        borderRadius: `${theme.shape.borderRadius}px`,
                        border: `1px solid ${result.ok ? theme.palette.success.main : theme.palette.error.main}`,
                    })}
                >
                    <Typography
                        variant={"caption"}
                        sx={{
                            color: result.ok ? "success.main" : "error.main",
                            whiteSpace: "pre-wrap",
                            wordBreak: "break-word",
                        }}
                    >
                        {result.message}
                        {result.firstMsg && `\n${t("node.actions.testConnection.firstMessage", "First message")}: ${result.firstMsg}`}
                    </Typography>
                </Box>
            )}
        </Box>
    );
};
