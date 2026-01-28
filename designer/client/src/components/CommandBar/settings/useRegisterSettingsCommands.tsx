import { Folder, ToggleOff } from "@mui/icons-material";
import type { Action } from "kbar";
import { Priority } from "kbar";
import { capitalize, lowerCase } from "lodash";
import React, { useCallback } from "react";

import { userSettingsRotate } from "../../../actions/nk/userSettings";
import { isNestedObject } from "../../../containers/settings/collapsibleSwitchList";
import { toNested } from "../../../containers/settings/SettingsView";
import { getUserSettingsMerged } from "../../../reducers/selectors/userSettings";
import type { Setting } from "../../../reducers/userSettings";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import { useRegisterCommands } from "../useRegisterCommands";

export function useRegisterSettingsCommands() {
    const dispatch = useAppDispatch();
    const settings = useAppSelector(getUserSettingsMerged);

    const flattenActions = useCallback(
        (nested: Record<string, any>, path = []): Action[] => {
            return Object.entries(nested).flatMap(([key, value]) => {
                const section = "Settings";
                const id = [...path, key].join(".");
                const name = capitalize(lowerCase(key));
                const parent = path.length ? `dir/${path.join(".")}` : undefined;
                if (!isNestedObject(value)) {
                    return {
                        id: `flag/${id}`,
                        perform: () => dispatch(userSettingsRotate(id as Setting)),
                        section,
                        name,
                        parent,
                        icon: <ToggleOff />,
                        priority: Priority.LOW,
                    };
                }

                const children = flattenActions(value, [...path, key]);
                return [{ id: `dir/${id}`, section, name, parent, icon: <Folder />, priority: Priority.LOW }, ...children];
            });
        },
        [dispatch],
    );

    useRegisterCommands(() => flattenActions(toNested(Object.entries(settings))), [flattenActions, settings]);
}
