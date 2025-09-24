import type { PropsWithChildren } from "react";
import React, { useEffect, useState } from "react";

import type { SettingsData } from "../actions/nk/assignSettings";
import { assignSettings } from "../actions/nk/assignSettings";
import { useUserSettings } from "../common/userSettings";
import LoaderSpinner from "../components/spinner/Spinner";
import HttpService from "../http/HttpService/instance";
import type { UserSettings } from "../reducers/userSettings";
import { useAppDispatch } from "../store/storeHelpers";

export function SettingsProvider({ children }: PropsWithChildren<unknown>): JSX.Element {
    const [data, setData] = useState<SettingsData>(null);
    const dispatch = useAppDispatch();

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
