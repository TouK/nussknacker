import loadable from "@loadable/component";
import React from "react";

import { useUserSettings } from "../../../common/useUserSettings";
import { getIsAssitantEnabled } from "../../../reducers/selectors/settings";
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
    const [showHelp] = useUserSettings("assistant.showHelp");

    if (!assistantEnabled || !showHelp) return null;
    return <AskAssistant {...props} />;
};
