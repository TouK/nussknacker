import * as React from "react";

import { render, screen } from "@testing-library/react";
import { jest } from "@jest/globals";
import { RawEditor } from "../../src/components/graph/node-modal/editors/expression/RawEditor";
import { Provider } from "react-redux";
import configureMockStore from "redux-mock-store/lib";
import { mockFieldError, mockValueChange } from "./helpers";

jest.mock("../../src/containers/theme");

const mockStore = configureMockStore();

const store = mockStore({
    settings: {
        processDefinitionData: {
            componentGroups: [],
            processDefinition: {},
            componentsConfig: {},
            additionalPropertiesConfig: {},
            edgesForNodes: [],
            customActions: [],
            defaultAsyncInterpretation: true,
        },
    },
    graphReducer: { processToDisplay: {} },
});

describe(RawEditor.name, () => {
    it("should display validation error when the field is required", () => {
        const { container } = render(
            <Provider store={store}>
                <RawEditor
                    readOnly={false}
                    className={""}
                    isMarked={false}
                    onValueChange={mockValueChange}
                    fieldErrors={mockFieldError}
                    expressionObj={{ language: "spel", expression: "test" }}
                    showValidation={true}
                    variableTypes={{}}
                />
            </Provider>,
        );

        const inputErrorIndicator = container.getElementsByClassName("node-input-with-error");
        expect(inputErrorIndicator.item(0)).toBeInTheDocument();
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
