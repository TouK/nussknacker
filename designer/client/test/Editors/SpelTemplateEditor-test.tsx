import { configureStore } from "@reduxjs/toolkit";
import { render, screen } from "@testing-library/react";
import "ace-builds/src-noconflict/ace";
import "ace-builds/src-noconflict/ext-language_tools";
import { HTML5toTouch } from "rdndmb-html5-to-touch";
import * as React from "react";
import { DndProvider } from "react-dnd-multi-backend";
import { Provider } from "react-redux";

import { SpelTemplateEditor } from "../../src/components/graph/node-modal/editors/expression/SpelTemplateEditor";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";
import { mockFieldErrors, mockValueChange } from "./helpers";

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
    },
    devTools: false,
});

describe("SpelTemplateEditor", () => {
    it("should display validation error when the field is required", () => {
        render(
            <NuThemeProvider>
                <Provider store={store}>
                    <DndProvider options={HTML5toTouch}>
                        <SpelTemplateEditor
                            readOnly={false}
                            isMarked={false}
                            onValueChange={mockValueChange}
                            fieldErrors={mockFieldErrors}
                            expressionObj={{ language: "spel", expression: "" }}
                            showValidation={true}
                            className={""}
                            variableTypes={{}}
                        />
                    </DndProvider>
                </Provider>
            </NuThemeProvider>,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
