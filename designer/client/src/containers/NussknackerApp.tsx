import { css } from "@emotion/css";
import { isEmpty } from "lodash";
import { HTML5toTouch } from "rdndmb-html5-to-touch";
import React from "react";
import { DndProvider } from "react-dnd-multi-backend";
import { Outlet } from "react-router-dom";

import { AiAssistantButton } from "../components/aiAssistant/components/AiAssistantButton";
import { MenuBar } from "../components/MenuBar";
import { VersionInfo } from "../components/versionInfo";
import { getLoggedUser } from "../reducers/selectors/settings";
import { useAppSelector } from "../store/configureStore";
import { WindowManager } from "../windowManager";
import { ConnectionErrorProvider } from "./connectionErrorProvider";
import { useRegisterTrackingEvents } from "./event-tracking";
import { useErrorRegister } from "./event-tracking/use-error-register";
import { Notifications } from "./Notifications";
import { useAnonymousStatistics } from "./useAnonymousStatistics";

export function NussknackerApp() {
    const loggedUser = useAppSelector(getLoggedUser);

    useAnonymousStatistics();
    useRegisterTrackingEvents();
    useErrorRegister();

    if (isEmpty(loggedUser)) {
        return null;
    }

    return (
        <>
            <DndProvider options={HTML5toTouch}>
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
                    <AiAssistantButton />
                </WindowManager>

                <ConnectionErrorProvider>
                    <Notifications />
                </ConnectionErrorProvider>
            </DndProvider>
            <VersionInfo />
        </>
    );
}
