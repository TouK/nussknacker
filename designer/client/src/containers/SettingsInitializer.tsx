import type { PropsWithChildren } from "react";
import React, { useEffect, useState } from "react";
import { useDispatch } from "react-redux";

import type { SettingsData } from "../actions/nk";
import { assignSettings } from "../actions/nk";
import { useUserSettings } from "../common/userSettings";
import LoaderSpinner from "../components/spinner/Spinner";
import HttpService from "../http/HttpService";
import type { UserSettings } from "../reducers/userSettings";

export function SettingsProvider({ children }: PropsWithChildren<unknown>): JSX.Element {
    const [data, setData] = useState<SettingsData>(null);
    const dispatch = useDispatch();

    const [, toggleUserSettings] = useUserSettings();
    useEffect(() => {
        window["$setUserFlag"] = (flag: keyof UserSettings) => toggleUserSettings([flag]);
    }, [toggleUserSettings]);

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
