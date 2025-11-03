import { configureStore } from "@reduxjs/toolkit";
import { render, screen } from "@testing-library/react";
import * as React from "react";
import "ace-builds/src-noconflict/ace";
import "ace-builds/src-noconflict/ext-language_tools";

import { SqlEditor } from "../../src/components/graph/node-modal/editors/expression/SqlEditor";
import { mockFieldErrors, mockFormatter, mockValueChange } from "./helpers";
import { TestProviders } from "./TestProviders";

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
            <TestProviders>
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
            </TestProviders>,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
