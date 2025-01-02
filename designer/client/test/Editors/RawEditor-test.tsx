import * as React from "react";

import { render, screen } from "@testing-library/react";
import { RawEditor } from "../../src/components/graph/node-modal/editors/expression/RawEditor";
import { Provider } from "react-redux";
import configureMockStore from "redux-mock-store/lib";
import { mockFieldErrors, mockValueChange } from "./helpers";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";
import { nodeInputWithError } from "../../src/components/graph/node-modal/NodeDetailsContent/NodeTableStyled";

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
    graphReducer: { history: { present: { scenario: { scenarioGraph: {} } } } },
});

describe(RawEditor.name, () => {
    it("should display validation error when the field is required", () => {
        const { container } = render(
            <NuThemeProvider>
                <Provider store={store}>
                    <RawEditor
                        readOnly={false}
                        className={""}
                        isMarked={false}
                        onValueChange={mockValueChange}
                        fieldErrors={mockFieldErrors}
                        expressionObj={{ language: "spel", expression: "test" }}
                        showValidation={true}
                        variableTypes={{}}
                    />
                </Provider>
            </NuThemeProvider>,
        );

        const inputErrorIndicator = container.getElementsByClassName(nodeInputWithError);
        expect(inputErrorIndicator.item(0)).toBeInTheDocument();
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
