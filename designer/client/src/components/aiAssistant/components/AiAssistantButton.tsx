import loadable from "@loadable/component";
import React from "react";

import { getIsAssitantEnabled } from "../../../reducers/selectors/settings";
import { getUserSettings } from "../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../store/storeHelpers";
import type { AskAssistantProps } from "./AskAssistant";

const AiAssistant = loadable(() => import("./OpenAssistantButton"), { fallback: null });
export const AiAssistantButton = () => {
    const assistantEnabled = useAppSelector(getIsAssitantEnabled);
    if (!assistantEnabled) return null;
    return <AiAssistant />;
};

const AskAssistant = loadable(() => import("./AskAssistant"), { fallback: null });
export const AskAssistantButton = (props: AskAssistantProps) => {
    const assistantEnabled = useAppSelector(getIsAssitantEnabled);
    const settings = useAppSelector(getUserSettings);

    if (!assistantEnabled || !settings["assistant.showHelp"]) return null;
    return <AskAssistant {...props} />;
};
