import { css } from "@emotion/css";
import React from "react";
import { useSelector } from "react-redux";
import { MenuBar } from "../components/MenuBar";
import { VersionInfo } from "../components/versionInfo";
import { getLoggedUser } from "../reducers/selectors/settings";
import { isEmpty } from "lodash";
import { Outlet } from "react-router-dom";
import { Notifications } from "./Notifications";
import { useAnonymousStatistics } from "./useAnonymousStatistics";
import { WindowManager } from "../windowManager";
import { ConnectionErrorProvider } from "./connectionErrorProvider";
import { useRegisterTrackingEvents } from "./event-tracking";
import { useErrorRegister } from "./event-tracking/use-error-register";

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
