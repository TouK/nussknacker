import { configureStore } from "@reduxjs/toolkit";
import { render, screen } from "@testing-library/react";
import * as React from "react";

import { SpelEditor } from "../../src/components/graph/node-modal/editors/expression/SpelEditor";
import { nodeInputWithError } from "../../src/components/graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { mockFieldErrors, mockValueChange } from "./helpers";
import { TestProviders } from "./TestProviders";

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

describe("SpelEditor", () => {
    it("should display validation error when the field is required", () => {
        const { container } = render(
            <TestProviders>
                <SpelEditor
                    readOnly={false}
                    className={""}
                    isMarked={false}
                    onValueChange={mockValueChange}
                    fieldErrors={mockFieldErrors}
                    expressionObj={{ language: "spel", expression: "test" }}
                    showValidation={true}
                    variableTypes={{}}
                />
            </TestProviders>,
        );

        const inputErrorIndicator = container.getElementsByClassName(nodeInputWithError);
        expect(inputErrorIndicator.item(0)).toBeInTheDocument();
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
