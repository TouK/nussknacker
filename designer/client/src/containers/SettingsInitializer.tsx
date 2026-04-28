import { useTheme } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { useEffect, useState } from "react";

import type { SettingsData } from "../actions/nk/assignSettings";
import { assignSettings } from "../actions/nk/assignSettings";
import { userSettingSet, userSettingsSetInitial, userSettingsToggle } from "../actions/nk/userSettings";
import { useUserSettings } from "../common/useUserSettings";
import LoaderSpinner from "../components/spinner/Spinner";
import HttpService from "../http/HttpService/instance";
import type { Setting, UserSettings } from "../reducers/userSettings";
import { waitForWindowValue } from "../reducers/waitForWindowValue";
import { useAppDispatch } from "../store/storeHelpers";

export type GlobalToggleUserFlag = (flag: Setting, value?: boolean) => Promise<boolean | "default">;

declare global {
    interface Window {
        $toggleUserFlag?: GlobalToggleUserFlag;
        $initialUserFlags?: UserSettings;
    }
}

export function SettingsProvider({ children }: PropsWithChildren<unknown>): React.JSX.Element {
    const [data, setData] = useState<SettingsData>(null);
    const dispatch = useAppDispatch();

    useEffect(() => {
        window.$toggleUserFlag = (flag, value) =>
            new Promise((resolve) => {
                if (value !== undefined) {
                    dispatch(userSettingSet(flag, value));
                } else {
                    dispatch(userSettingsToggle([flag]));
                }
                dispatch((_dispatch, getState) => {
                    resolve(getState().userSettings.values[flag]);
                });
            });
        waitForWindowValue("$initialUserFlags").then((flags) => {
            dispatch(userSettingsSetInitial(flags));
        });
    }, [dispatch]);

    const theme = useTheme();
    const [lightMode] = useUserSettings("debug.lightTheme");
    useEffect(() => {
        theme.setMode(lightMode ? "light" : "dark");
    }, [theme, lightMode]);

    useEffect(() => {
        HttpService.fetchSettingsWithAuth()
            .then((settings) => {
                setData(settings);
                dispatch(assignSettings(settings));
            })
            .catch((error) =>
                setData(() => {
                    throw error;
                }),
            );
    }, [dispatch]);

    return data ? <>{children}</> : <LoaderSpinner show />;
}
