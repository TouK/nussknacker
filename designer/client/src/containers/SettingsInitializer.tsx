import { useTheme } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { useEffect, useState } from "react";

import type { SettingsData } from "../actions/nk/assignSettings";
import { assignSettings } from "../actions/nk/assignSettings";
import { userSettingsSetInitial, userSettingsToggle } from "../actions/nk/userSettings";
import LoaderSpinner from "../components/spinner/Spinner";
import HttpService from "../http/HttpService/instance";
import { getUserSettings } from "../reducers/selectors/userSettings";
import type { Setting, UserSettings } from "../reducers/userSettings";
import { waitForWindowValue } from "../reducers/waitForWindowValue";
import { useAppDispatch, useAppSelector } from "../store/storeHelpers";

declare global {
    interface Window {
        $setUserFlag: (flag: Setting) => void;
        $initialUserFlags?: UserSettings;
    }
}

export function SettingsProvider({ children }: PropsWithChildren<unknown>): JSX.Element {
    const [data, setData] = useState<SettingsData>(null);
    const dispatch = useAppDispatch();

    useEffect(() => {
        window.$setUserFlag = (flag) => {
            dispatch(userSettingsToggle([flag]));
        };
        waitForWindowValue("$initialUserFlags").then((flags) => {
            dispatch(userSettingsSetInitial(flags));
        });
    }, [dispatch]);

    const theme = useTheme();
    const settings = useAppSelector(getUserSettings);
    const lightMode = settings["debug.lightTheme"];
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
