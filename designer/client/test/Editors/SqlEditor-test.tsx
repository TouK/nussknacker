import * as React from "react";
import "ace-builds/src-noconflict/ace";

import { render, screen } from "@testing-library/react";
import { HandledErrorType } from "../../src/components/graph/node-modal/editors/Validators";
import { jest } from "@jest/globals";
import "ace-builds/src-noconflict/ext-language_tools";
import SqlEditor from "../../src/components/graph/node-modal/editors/expression/SqlEditor";
import { Provider } from "react-redux";
import configureMockStore from "redux-mock-store/lib";

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
                    onValueChange={jest.fn()}
                    validators={[
                        {
                            description: () => "HandledErrorType.EmptyMandatoryParameter",
                            handledErrorType: HandledErrorType.EmptyMandatoryParameter,
                            validatorType: 0,
                            isValid: () => false,
                            message: () => "validation error",
                        },
                    ]}
                    expressionObj={{ language: "spel", expression: "" }}
                    showValidation={true}
                    className={""}
                    formatter={{ encode: jest.fn(() => "test"), decode: jest.fn() }}
                    variableTypes={{}}
                />
            </Provider>,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
