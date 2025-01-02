import * as React from "react";
import "ace-builds/src-noconflict/ace";

import { render, screen } from "@testing-library/react";
import "ace-builds/src-noconflict/ext-language_tools";
import { SqlEditor } from "../../src/components/graph/node-modal/editors/expression/SqlEditor";
import { Provider } from "react-redux";
import configureMockStore from "redux-mock-store/lib";
import { mockFieldErrors, mockFormatter, mockValueChange } from "./helpers";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";

const mockStore = configureMockStore();

const store = mockStore({
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
    graphReducer: { history: { present: { scenario: { scenarioGraph: {} } } } },
});

describe(SqlEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <NuThemeProvider>
                <Provider store={store}>
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
                </Provider>
            </NuThemeProvider>,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
