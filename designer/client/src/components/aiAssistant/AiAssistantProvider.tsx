import { AssistantRuntimeProvider, useLocalRuntime } from "@assistant-ui/react";
import type { ReactNode } from "react";
import React, { useEffect } from "react";

import { addListenerTyped, useAppDispatch } from "../../store/storeHelpers";
import { ModelAdapter } from "./ModelAdapter";
import { prepareHelpMessage } from "./prepareHelpMessage";

export function AiAssistantProvider({
    children,
}: Readonly<{
    children: ReactNode;
}>) {
    const runtime = useLocalRuntime(ModelAdapter, {});

    const dispatch = useAppDispatch();
    useEffect(() => {
        return dispatch(
            addListenerTyped("ASSISTANT_ASK", ({ question, realPrompt }) => {
                runtime.thread.append(prepareHelpMessage(question, realPrompt));
            }),
        );
    }, [dispatch, runtime.thread]);

    return <AssistantRuntimeProvider runtime={runtime}>{children}</AssistantRuntimeProvider>;
}
