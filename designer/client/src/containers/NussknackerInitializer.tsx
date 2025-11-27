import type { PropsWithChildren } from "react";
import React, { useCallback } from "react";

import { assignUser } from "../actions/nk/assignUser";
import HttpService from "../http/HttpService/instance";
import { getAuthenticationSettings } from "../reducers/selectors/settings";
import { useAppDispatch, useAppSelector } from "../store/storeHelpers";
import { AuthInitializer } from "./Auth/AuthInitializer";

function NussknackerInitializer({ children }: PropsWithChildren<unknown>): React.JSX.Element {
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
