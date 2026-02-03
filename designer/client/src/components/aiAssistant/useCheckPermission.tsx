import type { Tool } from "assistant-stream";
import { useCallback } from "react";

import { userSettingSet } from "../../actions/nk/userSettings";
import { getUserSettings } from "../../reducers/selectors/userSettings";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";

export const ToolUsePermission = {
    QUESTION: "execute_allowed",
    ANSWER: {
        YES: "allow",
        ALWAYS: "always allow",
        NO: "deny",
    },
};

type ToolExecuteFunction = Parameters<(Tool & { type: "frontend" })["execute"]>[1];

export function useCheckPermission() {
    const dispatch = useAppDispatch();
    const userSettings = useAppSelector(getUserSettings);

    return useCallback(
        async (toolName: string, { human }: ToolExecuteFunction) => {
            const alwaysExecuteFlag = `assistant.tools.${toolName}.executeWithoutAsking` as const;
            if (userSettings[alwaysExecuteFlag]) return true;

            const response = await human(ToolUsePermission.QUESTION);
            dispatch(userSettingSet(alwaysExecuteFlag, response === ToolUsePermission.ANSWER.ALWAYS));

            if (response === ToolUsePermission.ANSWER.NO) return Promise.reject("not allowed!");
        },
        [dispatch, userSettings],
    );
}
