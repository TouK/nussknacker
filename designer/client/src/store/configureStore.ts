/* eslint-disable i18next/no-literal-string */
import { useDispatch } from "react-redux";
import { applyMiddleware, createStore } from "redux";
import { composeWithDevTools } from "redux-devtools-extension";
import { persistStore } from "redux-persist";
import { createStateSyncMiddleware, initMessageListener } from "redux-state-sync";
import thunk from "redux-thunk";

import type { Action, ThunkDispatch } from "../actions/reduxTypes";
import { reducer } from "../reducers";
import { nodeValidationMiddleware } from "./nodeValidationMiddleware";

export default function configureStore() {
    const actionsBlacklist: Action["type"][] = ["PROCESS_STATE_LOADED", "UPDATE_BACKEND_NOTIFICATIONS", "SET_PENDING_CHANGES"];
    const store = createStore(
        reducer,
        composeWithDevTools({
            actionsBlacklist: ["RNS_SHOW_NOTIFICATION", "RNS_HIDE_NOTIFICATION", ...actionsBlacklist],
        })(
            applyMiddleware(
                thunk,
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
        ),
    );
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

export function useThunkDispatch() {
    return useDispatch<ThunkDispatch>();
}
