import { css } from "@emotion/css";
import { isEmpty } from "lodash";
import React from "react";
import { useSelector } from "react-redux";
import { Outlet } from "react-router-dom";
import { MenuBar } from "../components/MenuBar";
import { VersionInfo } from "../components/versionInfo";
import { getLoggedUser } from "../reducers/selectors/settings";
import { WindowManager } from "../windowManager";
import { ConnectionErrorProvider } from "./connectionErrorProvider";
import { useRegisterTrackingEvents } from "./event-tracking";
import { useErrorRegister } from "./event-tracking/use-error-register";
import { Notifications } from "./Notifications";
import { useAnonymousStatistics } from "./useAnonymousStatistics";

export function NussknackerApp() {
    const loggedUser = useSelector(getLoggedUser);

    useAnonymousStatistics();
    useRegisterTrackingEvents();
    useErrorRegister();

    if (isEmpty(loggedUser)) {
        return null;
    }

    return (
        <>
            <WindowManager
                className={css({
                    flex: 1,
                    display: "flex",
                    "& *": {
                        scrollPadding: 40,
                    },
                })}
            >
                <div
                    id="app-container"
                    className={css({
                        flex: 1,
                        display: "grid",
                        gridTemplateRows: "auto 1fr",
                        alignItems: "stretch",
                    })}
                >
                    <MenuBar />
                    <main className={css({ overflow: "auto" })}>
                        <Outlet />
                    </main>
                </div>
            </WindowManager>

            <ConnectionErrorProvider>
                <Notifications />
            </ConnectionErrorProvider>
            <VersionInfo />
        </>
    );
}
