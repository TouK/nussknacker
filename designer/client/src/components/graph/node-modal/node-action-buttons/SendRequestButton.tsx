import { Alert, Box, styled } from "@mui/material";
import React, { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";

import { setTestData } from "../../../../actions/nk/displayTestResults";
import HttpService from "../../../../http/HttpService";
import { getProcessName } from "../../../../reducers/selectors/graph";
import type { NodeType } from "../../../../types";
import { LoadingButton } from "../../../../windowManager/LoadingButton";
import { useSourceParameters } from "../../../modals/AdhocTesting/useAdhocTestingAction";

const StyledLoadingButton = styled(LoadingButton)(({ theme }) => ({
    fontSize: "12px",
    textTransform: "inherit",
    padding: theme.spacing(0.5, 1),
    margin: 0,
    ":not(:last-child)": {
        marginRight: 0,
    },
}));

interface Props {
    disabled: boolean;
    node: NodeType;
    expression: string;
}
export const SendRequestButton = ({ disabled, node, expression }: Props) => {
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
            <StyledLoadingButton disabled={disabled} title={t("node.actions.sendRequest", "Send Request")} action={handleSendHttpRequest} />
            {showInfoAfterSendData && (
                <Alert
                    icon={false}
                    severity="success"
                    sx={{ width: "80%" }}
                    onClose={() => {
                        setShowInfoAfterSendData(false);
                    }}
                >
                    TODO
                </Alert>
            )}
        </Box>
    );
};
