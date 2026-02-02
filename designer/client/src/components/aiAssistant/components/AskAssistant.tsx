import { Button } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";

import { assistantAsk } from "../../../actions/assistantActions";
import { useAppDispatch } from "../../../store/storeHelpers";

export type AskAssistantProps = PropsWithChildren<{ question: string; realPrompt?: string; className?: string }>;

function AskAssistant({ question, realPrompt = question, className, children }: AskAssistantProps) {
    const { t } = useTranslation();
    const dispatch = useAppDispatch();

    const onClick = useCallback(() => {
        dispatch(assistantAsk(question, realPrompt));
    }, [realPrompt, dispatch, question]);

    return (
        <Button
            className={className}
            variant="contained"
            size="small"
            onClick={onClick}
            color="inherit"
            sx={(theme) => ({
                marginX: 0.25,
                paddingX: 0.5,
                paddingY: 0,
                boxShadow: "none",
                borderRadius: theme.shape.borderRadius / 8,
                fontSize: "0.65rem",
                textTransform: "unset",
                minWidth: 0,
                whiteSpace: "nowrap",
            })}
        >
            {children || t("assistant.ask", `explain?`)}
        </Button>
    );
}

export default AskAssistant;
