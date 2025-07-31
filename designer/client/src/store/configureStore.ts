/* eslint-disable i18next/no-literal-string */
import { configureStore } from "@reduxjs/toolkit";
import { useDispatch } from "react-redux";
import { persistStore } from "redux-persist";
import { createStateSyncMiddleware, initMessageListener } from "redux-state-sync";

import type { Action } from "../actions/reduxTypes";
import { rootReducer } from "../reducers";
import { nodeValidationMiddleware } from "./nodeValidationMiddleware";

export default function init() {
    // avoid polluting devtools with frequent refresh actions
    const actionsBlacklist: Action["type"][] = [
        "PROCESS_STATE_LOADED",
        "UPDATE_BACKEND_NOTIFICATIONS",
        "SET_PENDING_CHANGES",
        "FETCH_LIVE_DATA",
        "DISPLAY_LIVE_DATA",
    ];

    const store = configureStore({
        reducer: rootReducer,
        middleware: (getDefaultMiddleware) =>
            getDefaultMiddleware().concat(
                createStateSyncMiddleware({
                    whitelist: [
                        "TOGGLE_SETTINGS",
                        "SET_SETTINGS",
                        "REGISTER_TOOLBARS",
                        "RESET_TOOLBARS",
                        "MOVE_TOOLBAR",
                        "TOGGLE_TOOLBAR",
                        "TOGGLE_ALL_TOOLBARS",
                        "TOGGLE_PANEL",
                        "TOGGLE_COMPONENT_GROUP_TOOLBOX",
                    ],
                }),
                nodeValidationMiddleware([
                    "NODE_ADDED",
                    "DELETE_NODES",
                    "NODES_CONNECTED",
                    "NODES_DISCONNECTED",
                    "NODES_WITH_EDGES_ADDED",
                    "STICKY_NOTE_UPDATED",
                ]),
            ),
        devTools: {
            actionsDenylist: ["RNS_SHOW_NOTIFICATION", "RNS_HIDE_NOTIFICATION", ...actionsBlacklist],
        },
    });

    const persistor = persistStore(store);
    initMessageListener(store);

    if (module.hot) {
        module.hot.accept("../reducers", () => {
            // eslint-disable-next-line @typescript-eslint/no-var-requires
            const nextReducer = require("../reducers").reducer;
            store.replaceReducer(nextReducer);
        });
    }

    return { store, persistor };
}

type Store = ReturnType<typeof init>["store"];

export type AppDispatch = Store["dispatch"];
export const useAppDispatch = () => useDispatch<AppDispatch>();
