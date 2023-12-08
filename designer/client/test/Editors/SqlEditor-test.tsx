import * as React from "react";
import "ace-builds/src-noconflict/ace";

import { render, screen } from "@testing-library/react";
import { jest } from "@jest/globals";
import "ace-builds/src-noconflict/ext-language_tools";
import { SqlEditor } from "../../src/components/graph/node-modal/editors/expression/SqlEditor";
import { Provider } from "react-redux";
import configureMockStore from "redux-mock-store/lib";
import { mockFieldError, mockFormatter, mockValueChange } from "./helpers";

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

describe(SqlEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <Provider store={store}>
                <SqlEditor
                    readOnly={false}
                    isMarked={false}
                    onValueChange={mockValueChange}
                    fieldErrors={mockFieldError}
                    expressionObj={{ language: "spel", expression: "" }}
                    showValidation={true}
                    className={""}
                    formatter={mockFormatter}
                    variableTypes={{}}
                />
            </Provider>,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
