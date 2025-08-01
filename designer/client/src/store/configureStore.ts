/* eslint-disable i18next/no-literal-string */
import { configureStore } from "@reduxjs/toolkit";
import { useDispatch, useSelector } from "react-redux";
import type { Store } from "redux";
import { persistStore } from "redux-persist";
import { createStateSyncMiddleware, initMessageListener } from "redux-state-sync";
import type { ThunkMiddleware } from "redux-thunk";
import { thunk } from "redux-thunk";

import type { Action } from "../actions/reduxTypes";
import type { RootState } from "../reducers";
import { rootReducer } from "../reducers";
import { nodeValidationMiddleware } from "./nodeValidationMiddleware";

// avoid polluting devtools with frequent refresh actions
const actionsBlacklist: Action["type"][] = [
    "PROCESS_STATE_LOADED",
    "UPDATE_BACKEND_NOTIFICATIONS",
    "SET_PENDING_CHANGES",
    "FETCH_LIVE_DATA",
    "DISPLAY_LIVE_DATA",
];

export const store = configureStore({
    reducer: rootReducer,
    middleware: (getDefaultMiddleware) =>
        getDefaultMiddleware({
            serializableCheck: false, // we still have non fixed antipatterns
            thunk: false, // need to disable and provide own, typed thunk
        })
            .prepend(thunk as ThunkMiddleware<RootState, Action>)
            .concat(
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

export const persistor = persistStore(store as Store);
initMessageListener(store);

if (module.hot) {
    module.hot.accept("../reducers", () => {
        // eslint-disable-next-line @typescript-eslint/no-var-requires
        const nextReducer = require("../reducers").reducer;
        store.replaceReducer(nextReducer);
    });
}

export type AppDispatch = typeof store.dispatch;
export type AppState = ReturnType<typeof store.getState>;
export const useAppDispatch = useDispatch.withTypes<AppDispatch>();
export const useAppSelector = useSelector.withTypes<AppState>();
