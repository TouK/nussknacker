import { jest } from "@jest/globals";
import { configureStore } from "@reduxjs/toolkit";

import { render, screen } from "@testing-library/react";
import * as React from "react";
import { Provider } from "react-redux";
import { DictParameterEditor } from "../../src/components/graph/node-modal/editors/expression/DictParameterEditor/DictParameterEditor";
import { nodeInputWithError } from "../../src/components/graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";
import { mockFieldErrors, mockFormatter, mockValueChange } from "./helpers";

jest.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key) => key,
        i18n: { changeLanguage: () => {} },
    }),
}));

const store = configureStore({
    reducer: (state) => state,
    preloadedState: {
        graphReducer: { present: { scenario: {} } },
    },
    devTools: false,
});

const ComponentWrapper = ({ children }) => (
    <NuThemeProvider>
        <Provider store={store}>{children}</Provider>
    </NuThemeProvider>
);

describe("DictParameterEditor", () => {
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
                    editorConfig={{ dictId: "long_dict" }}
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
                    editorConfig={{ dictId: "long_dict" }}
                />
            </ComponentWrapper>,
        );

        expect(screen.getByRole("combobox")).not.toHaveClass(nodeInputWithError);
        expect(screen.queryByText("validation error")).not.toBeInTheDocument();
    });
});
