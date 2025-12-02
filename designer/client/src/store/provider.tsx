import type { PropsWithChildren } from "react";
import React from "react";
import { Provider } from "react-redux";
import { PersistGate } from "redux-persist/integration/react";

import { getLoggedUserId } from "../reducers/selectors/settings";
import { setupStore } from "./configureStore";
import { waitForFirstValue } from "./waitForFirstValue";

const { store, persistor } = setupStore();

export const StoreProvider = ({ children }: PropsWithChildren<unknown>): React.JSX.Element => (
    <Provider store={store}>
        <PersistGate loading={null} persistor={persistor}>
            {children}
        </PersistGate>
    </Provider>
);

export { store };

// expose userId getter for analytics
window["__userId"] = waitForFirstValue(store, getLoggedUserId);
