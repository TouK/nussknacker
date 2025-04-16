import { styled } from "@mui/material";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch } from "react-redux";

import { setTestData } from "../../../actions/nk/displayTestResults";
import HttpService from "../../../http/HttpService";
import { LoadingButton } from "../../../windowManager/LoadingButton";
import { ExpressionLang } from "./editors/expression/types";

const StyledLoadingButton = styled(LoadingButton)(() => ({
    fontSize: "12px",
    margin: 0,
}));

interface Props {
    disabled: boolean;
    expression: string;
    nodeId: string;
}
export const SendRequestButton = ({ expression, disabled, nodeId }: Props) => {
    const dispatch = useDispatch();
    const { t } = useTranslation();

    const handleSendHttpRequest = useCallback(async () => {
        try {
            await HttpService.sendHttpRequest(JSON.parse(expression));
            dispatch(
                setTestData({
                    sourceId: nodeId,
                    parameterExpressions: { expression: { expression, language: ExpressionLang.JSON } },
                }),
            );
        } catch (error) {
            console.error("Error sending request:", error);
        }
    }, [dispatch, expression, nodeId]);

    return <StyledLoadingButton disabled={disabled} title={t("node.actions.sendRequest", "Send Request")} action={handleSendHttpRequest} />;
};
