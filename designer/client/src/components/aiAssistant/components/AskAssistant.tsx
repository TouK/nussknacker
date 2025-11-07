import { Button } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";

import { assistantAsk } from "../../../actions/assistantActions";
import { getScenarioGraph } from "../../../reducers/selectors/graph";
import { getProcessState } from "../../../reducers/selectors/scenarioState";
import { getUserSettings } from "../../../reducers/selectors/userSettings";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";

export type AskAssistantProps = PropsWithChildren<{ question: string; realPrompt?: string }>;

function AskAssistant({ question, realPrompt = question, children }: AskAssistantProps) {
    const { t } = useTranslation();
    const dispatch = useAppDispatch();
    const graph = useAppSelector(getScenarioGraph);
    const { status } = useAppSelector(getProcessState);
    const settings = useAppSelector(getUserSettings);

    const onClick = useCallback(() => {
        dispatch(
            assistantAsk(
                question,
                realPrompt && settings["assistant.includeScenarioData"]
                    ? `${realPrompt}. (Here you have some more raw internal data: ${JSON.stringify({
                          graph,
                          status,
                      })}. Remember that I can't fully see that data. I only edit some of it directly, while other parts are displayed differently. This data is provided so you can better understand the context, all my expressions, nodes and connections should be here. I mainly edit expressions that are sometimes wrapped in a more user-friendly layer.)`
                    : realPrompt,
            ),
        );
    }, [dispatch, question, settings, realPrompt, graph, status]);

    return (
        <Button
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
