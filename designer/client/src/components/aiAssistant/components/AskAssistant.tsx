import { useThread } from "@assistant-ui/react";
import { Button } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { assistantAsk } from "../../../actions/assistantActions";
import { getScenarioGraph, isFragment } from "../../../reducers/selectors/graph";
import { getProcessState } from "../../../reducers/selectors/scenarioState";
import { getUserSettings } from "../../../reducers/selectors/userSettings";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";

export type AskAssistantProps = PropsWithChildren<{ question: string; realPrompt?: string }>;

function AskAssistant({ question, realPrompt = question, children }: AskAssistantProps) {
    const { t } = useTranslation();
    const thread = useThread();
    const dispatch = useAppDispatch();
    const graph = useAppSelector(getScenarioGraph);
    const state = useAppSelector(getProcessState);
    const fragment = useAppSelector(isFragment);
    const settings = useAppSelector(getUserSettings);

    const contextData = useMemo(
        () => JSON.stringify(fragment ? { scenario: graph, isFragment: true } : { scenario: graph, status: state?.status }),
        [fragment, graph, state?.status],
    );

    const [askedOnce, setAskedOnce] = useState(false);
    useEffect(() => {
        if (thread.messages.length < 1) {
            setAskedOnce(false);
        }
    }, [thread.messages.length]);

    const contextSetup = useMemo(() => {
        const context = askedOnce
            ? [`This time I have this raw internal data: ${contextData}.`]
            : [
                  `Here you have some more raw internal data: ${contextData}.`,
                  `Remember that I can't fully see that data. I only edit some of it directly, while other parts are displayed differently.`,
                  `This data is provided so you can better understand the context, all my expressions, nodes and connections should be here.`,
                  `I mainly edit expressions in leaf values that are sometimes wrapped in a more user-friendly layer.`,
                  `Please answer briefly, without unnecessary repetitions.`,
              ];
        return context.join("\n");
    }, [askedOnce, contextData]);

    const onClick = useCallback(() => {
        let prompt = realPrompt;
        if (prompt && settings["assistant.includeScenarioData"]) {
            prompt = `${realPrompt}. (${contextSetup})`;
        }
        dispatch(assistantAsk(question, prompt));
        setAskedOnce(true);
    }, [dispatch, question, realPrompt, settings, contextSetup]);

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
