import * as React from "react";
import "ace-builds/src-noconflict/ace";

import { render, screen } from "@testing-library/react";
import "ace-builds/src-noconflict/ext-language_tools";
import { Provider } from "react-redux";
import configureMockStore from "redux-mock-store/lib";
import { SpelTemplateEditor } from "../../src/components/graph/node-modal/editors/expression/SpelTemplateEditor";
import { mockFieldErrors, mockValueChange } from "./helpers";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";

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

describe(SpelTemplateEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <NuThemeProvider>
                <Provider store={store}>
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
                </Provider>
            </NuThemeProvider>,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
