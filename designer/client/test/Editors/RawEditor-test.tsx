import { render, screen } from "@testing-library/react";
import { HTML5toTouch } from "rdndmb-html5-to-touch";
import * as React from "react";
import { DndProvider } from "react-dnd-multi-backend";
import { Provider } from "react-redux";
import configureMockStore from "redux-mock-store/lib";

import { SpelEditor } from "../../src/components/graph/node-modal/editors/expression/SpelEditor";
import { nodeInputWithError } from "../../src/components/graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";
import { mockFieldErrors, mockValueChange } from "./helpers";

const mockStore = configureMockStore();

const store = mockStore({
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
});

describe("SpelEditor", () => {
    it("should display validation error when the field is required", () => {
        const { container } = render(
            <NuThemeProvider>
                <Provider store={store}>
                    <DndProvider options={HTML5toTouch}>
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
                    </DndProvider>
                </Provider>
            </NuThemeProvider>,
        );

        const inputErrorIndicator = container.getElementsByClassName(nodeInputWithError);
        expect(inputErrorIndicator.item(0)).toBeInTheDocument();
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
