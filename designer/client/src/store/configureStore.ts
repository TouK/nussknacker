/* eslint-disable i18next/no-literal-string */
import type { ThunkDispatch } from "@reduxjs/toolkit";
import { configureStore, createListenerMiddleware } from "@reduxjs/toolkit";
import type { Store } from "redux";
import { persistStore } from "redux-persist";
import { createStateSyncMiddleware, initMessageListener } from "redux-state-sync";
import type { ThunkMiddleware } from "redux-thunk";
import { thunk } from "redux-thunk";

import type { Action } from "../actions/reduxTypes";
import type { RootState } from "../reducers";
import { rootReducer } from "../reducers";
import { scenarioValidationMiddleware } from "./scenarioValidationMiddleware";

// avoid polluting devtools with frequent refresh actions
const actionsBlacklist: Action["type"][] = [
    "PROCESS_STATE_LOADED",
    "UPDATE_BACKEND_NOTIFICATIONS",
    "SET_PENDING_CHANGES",
    "FETCH_LIVE_DATA",
    "DISPLAY_LIVE_DATA",
];

export const setupStore = () => {
    const listenerMiddleware = createListenerMiddleware<RootState, ThunkDispatch<RootState, unknown, Action>>();

    const store = configureStore({
        reducer: rootReducer,
        middleware: (getDefaultMiddleware) =>
            getDefaultMiddleware({
                immutableCheck: false,
                serializableCheck: false, // we still have non fixed antipatterns
                thunk: false, // need to disable and provide own, typed thunk
            })
                .prepend(listenerMiddleware.middleware, thunk as ThunkMiddleware<RootState, Action>)
                .concat(
                    createStateSyncMiddleware({
                        whitelist: [
                            "USERSETTING_SET",
                            "USERSETTINGS_RESET",
                            "USERSETTINGS_DEFAULTS_LOADED",
                            "REGISTER_TOOLBARS",
                            "RESET_TOOLBARS",
                            "MOVE_TOOLBAR",
                            "TOGGLE_TOOLBAR",
                            "TOGGLE_ALL_TOOLBARS",
                            "TOGGLE_PANEL",
                            "TOGGLE_COMPONENT_GROUP_TOOLBOX",
                        ],
                    }),
                    scenarioValidationMiddleware([
                        "NODE_ADDED",
                        "DELETE_NODES",
                        "NODES_CONNECTED",
                        "NODES_DISCONNECTED",
                        "NODES_WITH_EDGES_ADDED",
                        "STICKY_NOTE_UPDATED",
                        "NODE_DETAILS_CLOSED",
                        "TOOL_CLOSED",
                    ]),
                ),
        devTools: {
            actionsDenylist: ["RNS_SHOW_NOTIFICATION", "RNS_HIDE_NOTIFICATION", ...actionsBlacklist],
        },
    });

    const persistor = persistStore(store as Store);
    initMessageListener(store);

    if (module.hot) {
        module.hot.accept("../reducers", () => {
            // eslint-disable-next-line @typescript-eslint/no-var-requires
            const nextReducer = require("../reducers").reducer;
            store.replaceReducer(nextReducer);
        });
    }

    return { store, persistor };
};
