import { configureStore } from "@reduxjs/toolkit";

import { render, screen } from "@testing-library/react";
import * as React from "react";
import "ace-builds/src-noconflict/ace";
import { Provider } from "react-redux";
import { JsonEditor } from "../../src/components/graph/node-modal/editors/expression/JsonEditor";
import "ace-builds/src-noconflict/ext-language_tools";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";
import { mockFieldErrors, mockValueChange } from "./helpers";

const store = configureStore({
    reducer: (state) => state,
    preloadedState: {
        settings: { featuresSettings: { remoteEnvironment: { targetEnvironmentId: "remote environment" } } },
    },
    devTools: false,
});

describe("JSON Editor", () => {
    it("should display validation error when the field is required", () => {
        render(
            <NuThemeProvider>
                <Provider store={store}>
                    <JsonEditor
                        onValueChange={mockValueChange}
                        fieldErrors={mockFieldErrors}
                        expressionObj={{ language: "spel", expression: "" }}
                        showValidation={true}
                        className={""}
                        fieldName={""}
                    />
                </Provider>
            </NuThemeProvider>,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
