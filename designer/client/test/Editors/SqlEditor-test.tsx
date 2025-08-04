import { configureStore } from "@reduxjs/toolkit";
import { render, screen } from "@testing-library/react";
import { HTML5toTouch } from "rdndmb-html5-to-touch";
import * as React from "react";
import "ace-builds/src-noconflict/ace";
import "ace-builds/src-noconflict/ext-language_tools";
import { DndProvider } from "react-dnd-multi-backend";
import { Provider } from "react-redux";

import { SqlEditor } from "../../src/components/graph/node-modal/editors/expression/SqlEditor";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";
import { mockFieldErrors, mockFormatter, mockValueChange } from "./helpers";

const store = configureStore({
    reducer: (state) => state,
    preloadedState: {
        settings: {
            processDefinitionData: {
                componentGroups: [],
                components: {},
                classes: [],
                componentsConfig: {},
                additionalPropertiesConfig: {},
                edgesForNodes: [],
            },
        },
        graphReducer: { present: { scenario: {} } },
    },
    devTools: false,
});

describe("SqlEditor", () => {
    it("should display validation error when the field is required", () => {
        render(
            <NuThemeProvider>
                <Provider store={store}>
                    <DndProvider options={HTML5toTouch}>
                        <SqlEditor
                            readOnly={false}
                            isMarked={false}
                            onValueChange={mockValueChange}
                            fieldErrors={mockFieldErrors}
                            expressionObj={{ language: "spel", expression: "" }}
                            showValidation={true}
                            className={""}
                            formatter={mockFormatter}
                            variableTypes={{}}
                        />
                    </DndProvider>
                </Provider>
            </NuThemeProvider>,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
