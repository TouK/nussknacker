import * as React from "react";

import { render, screen } from "@testing-library/react";
import { jest } from "@jest/globals";
import { mockFormatter, mockFieldErrors, mockValueChange } from "./helpers";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";
import { DictParameterEditor } from "../../src/components/graph/node-modal/editors/expression/DictParameterEditor";
import { Provider } from "react-redux";
import configureMockStore from "redux-mock-store/lib";
import { nodeInputWithError } from "../../src/components/graph/node-modal/NodeDetailsContent/NodeTableStyled";

jest.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key) => key,
        i18n: { changeLanguage: () => {} },
    }),
}));

const mockedParam = {
    name: "LongDict",
    typ: {
        display: "Unknown",
        type: "Unknown",
        refClazzName: "java.lang.Object",
        params: [],
    },
    editor: {
        dictId: "long_dict",
        type: "DictParameterEditor",
    },
    defaultValue: {
        language: "dictKeyWithLabel",
        expression: "",
    },
    additionalVariables: {},
    variablesToHide: [],
    branchParam: false,
    hintText: null,
    label: "LongDict",
};

const mockStore = configureMockStore();

const store = mockStore({
    graphReducer: {
        scenario: {
            processingType: "streaming-dev",
        },
        history: { present: { scenario: { scenarioGraph: {} } } },
    },
});

const ComponentWrapper = ({ children }) => (
    <NuThemeProvider>
        <Provider store={store}>{children}</Provider>
    </NuThemeProvider>
);

describe(DictParameterEditor.name, () => {
    it("should display validation error when the field contain errors", () => {
        render(
            <ComponentWrapper>
                <DictParameterEditor
                    readOnly={false}
                    className={""}
                    onValueChange={mockValueChange}
                    fieldErrors={mockFieldErrors}
                    expressionObj={{ language: "spel", expression: "" }}
                    formatter={mockFormatter}
                    showValidation={true}
                    param={mockedParam}
                />
            </ComponentWrapper>,
        );

        expect(screen.getByRole("combobox")).toHaveClass(nodeInputWithError);
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });

    it("should not show validation error when the field not contain errors", () => {
        render(
            <ComponentWrapper>
                <DictParameterEditor
                    readOnly={false}
                    className={""}
                    onValueChange={mockValueChange}
                    fieldErrors={[]}
                    expressionObj={{ language: "spel", expression: "" }}
                    formatter={mockFormatter}
                    showValidation={true}
                    param={mockedParam}
                />
            </ComponentWrapper>,
        );

        expect(screen.getByRole("combobox")).not.toHaveClass(nodeInputWithError);
        expect(screen.queryByText("validation error")).not.toBeInTheDocument();
    });
});
