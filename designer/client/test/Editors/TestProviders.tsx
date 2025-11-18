import { configureStore } from "@reduxjs/toolkit";
import * as React from "react";
import { PropsWithChildren } from "react";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";
import { Provider } from "react-redux";
import { HTML5toTouch } from "rdndmb-html5-to-touch";
import { DndProvider } from "react-dnd-multi-backend";

const store = configureStore({
    reducer: (state) => state,
    preloadedState: {
        settings: {
            processDefinitionData: {
                componentGroups: [],
                processDefinition: {},
                componentsConfig: {},
                additionalPropertiesConfig: {},
                edgesForNodes: [],
                defaultAsyncInterpretation: true,
            },
        },
        graphReducer: { present: { scenario: {} } },
        userSettings: {
            defaults: {},
            values: {},
        },
    },
    devTools: false,
});

export const TestProviders = ({ children }: PropsWithChildren) => {
    return (
        <NuThemeProvider>
            <Provider store={store}>
                <DndProvider options={HTML5toTouch}>{children}</DndProvider>
            </Provider>
        </NuThemeProvider>
    );
};
