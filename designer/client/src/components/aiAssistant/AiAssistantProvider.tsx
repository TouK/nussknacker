import { AssistantRuntimeProvider, useLocalRuntime } from "@assistant-ui/react";
import type { ReactNode } from "react";
import React, { useCallback, useEffect } from "react";

import { useUserSettings } from "../../common/useUserSettings";
import { addListenerTyped, useAppDispatch } from "../../store/storeHelpers";
import { DebugAiTool } from "./debug/DebugAiTool";
import { createModelAdapter } from "./ModelAdapter";
import { prepareHelpMessage } from "./prepareHelpMessage";

export function AiAssistantProvider({
    children,
}: Readonly<{
    children: ReactNode;
}>) {
    const [debug] = useUserSettings("debug.mockAssistant");

    const getAdapter = useCallback(() => createModelAdapter(debug), [debug]);
    const runtime = useLocalRuntime(getAdapter(), {});

    const dispatch = useAppDispatch();
    useEffect(() => {
        return dispatch(
            addListenerTyped("ASSISTANT_ASK", ({ question, realPrompt }) => {
                runtime.thread.append(prepareHelpMessage(question, realPrompt));
            }),
        );
    }, [dispatch, runtime.thread]);

    return (
        <AssistantRuntimeProvider runtime={runtime}>
            {debug ? <DebugAiTool /> : null}
            {children}
        </AssistantRuntimeProvider>
    );
}
