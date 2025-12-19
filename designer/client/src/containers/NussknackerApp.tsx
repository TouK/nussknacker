import { css } from "@emotion/css";
import { isEmpty } from "lodash";
import { HTML5toTouch } from "rdndmb-html5-to-touch";
import React from "react";
import { DndProvider } from "react-dnd-multi-backend";
import Snowfall from "react-snowfall";

import { AiAssistantButton } from "../components/aiAssistant/components/AiAssistantButton";
import { MenuBar } from "../components/MenuBar";
import { VersionInfo } from "../components/versionInfo";
import { getLoggedUser } from "../reducers/selectors/settings";
import { getUserSettings } from "../reducers/selectors/userSettings";
import { useAppSelector } from "../store/storeHelpers";
import { WindowManager } from "../windowManager/WindowManager";
import { ConnectionErrorProvider } from "./connectionErrorProvider/ConnectionErrorProvider";
import { useErrorRegister } from "./event-tracking/use-error-register";
import { useRegisterTrackingEvents } from "./event-tracking/use-register-tracking-events";
import { MainOutlet } from "./MainOutlet";
import { Notifications } from "./Notifications";
import { useAnonymousStatistics } from "./useAnonymousStatistics";

export function NussknackerApp() {
    const loggedUser = useAppSelector(getLoggedUser);
    const settings = useAppSelector(getUserSettings);
    const isSnowing = settings["scenario.isItSnowing"];

    useAnonymousStatistics();
    useRegisterTrackingEvents();
    useErrorRegister();

    if (isEmpty(loggedUser)) {
        return null;
    }

    return (
        <>
            {isSnowing && (
                <Snowfall
                    style={{
                        position: "fixed",
                        inset: 0,
                        zIndex: 9999, // higher than JointJS
                        pointerEvents: "none",
                    }}
                />
            )}
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
                        <MainOutlet />
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
