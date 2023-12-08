import * as React from "react";
import "ace-builds/src-noconflict/ace";

import { render, screen } from "@testing-library/react";
import { jest } from "@jest/globals";
import "ace-builds/src-noconflict/ext-language_tools";
import { Provider } from "react-redux";
import configureMockStore from "redux-mock-store/lib";
import { SpelTemplateEditor } from "../../src/components/graph/node-modal/editors/expression/SpelTemplateEditor";
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

describe(SpelTemplateEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <Provider store={store}>
                <SpelTemplateEditor
                    readOnly={false}
                    isMarked={false}
                    onValueChange={mockValueChange}
                    fieldErrors={mockFieldError}
                    expressionObj={{ language: "spel", expression: "" }}
                    showValidation={true}
                    className={""}
                    variableTypes={{}}
                />
            </Provider>,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
