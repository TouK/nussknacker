/* eslint-disable i18next/no-literal-string */
import { applyMiddleware, createStore } from "redux";
import { composeWithDevTools } from "redux-devtools-extension";
import thunk from "redux-thunk";
import { persistStore } from "redux-persist";
import { reducer } from "../reducers";
import { ThunkDispatch } from "../actions/reduxTypes";
import { useDispatch } from "react-redux";
import { createStateSyncMiddleware, initMessageListener } from "redux-state-sync";
import { nodeValidationMiddleware } from "./nodeValidationMiddleware";

export default function configureStore() {
    const store = createStore(
        reducer,
        composeWithDevTools({ actionsBlacklist: ["UPDATE_BACKEND_NOTIFICATIONS", "RNS_SHOW_NOTIFICATION", "RNS_HIDE_NOTIFICATION"] })(
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
                nodeValidationMiddleware(["NODE_ADDED", "DELETE_NODES", "NODES_CONNECTED", "NODES_DISCONNECTED", "NODES_WITH_EDGES_ADDED"]),
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
