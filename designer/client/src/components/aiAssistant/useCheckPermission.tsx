import type { Tool } from "assistant-stream";
import { useCallback } from "react";

import { userSettingSet } from "../../actions/nk/userSettings";
import { getUserSettings } from "../../reducers/selectors/userSettings";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";

type ToolExecuteFunction = Parameters<(Tool & { type: "frontend" })["execute"]>[1];

export function useCheckPermission() {
    const dispatch = useAppDispatch();
    const userSettings = useAppSelector(getUserSettings);

    return useCallback(
        async (toolName: string, { human }: ToolExecuteFunction) => {
            const alwaysExecuteFlag = `assistant.tools.${toolName}.executeWithoutAsking` as const;
            if (userSettings[alwaysExecuteFlag]) return true;

            const response = await human("execute allowed?");
            dispatch(userSettingSet(alwaysExecuteFlag, response === "always"));

            if (response === "no") return Promise.reject("not allowed!");
        },
        [dispatch, userSettings],
    );
}
