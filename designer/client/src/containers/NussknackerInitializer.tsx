import type { PropsWithChildren } from "react";
import React, { useCallback } from "react";

import { assignUser } from "../actions/nk";
import HttpService from "../http/HttpService";
import { getAuthenticationSettings } from "../reducers/selectors/settings";
import { useAppDispatch, useAppSelector } from "../store/configureStore";
import { AuthInitializer } from "./Auth";

function NussknackerInitializer({ children }: PropsWithChildren<unknown>): JSX.Element {
    const dispatch = useAppDispatch();

    const onAuth = useCallback(
        () =>
            HttpService.fetchLoggedUser().then(({ data }) => {
                dispatch(assignUser(data));
            }),
        [dispatch],
    );

    const authenticationSettings = useAppSelector(getAuthenticationSettings);

    return (
        <AuthInitializer authenticationSettings={authenticationSettings} onAuthFulfilled={onAuth}>
            {children}
        </AuthInitializer>
    );
}

export default NussknackerInitializer;
