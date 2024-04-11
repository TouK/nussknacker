import * as React from "react";

import { render, screen } from "@testing-library/react";
import { jest } from "@jest/globals";
import { DateTimeEditor } from "../../src/components/graph/node-modal/editors/expression/DateTimeEditor";
import { mockFormatter, mockFieldErrors, mockValueChange } from "./helpers";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";
import { nodeInputWithError } from "../../src/components/graph/node-modal/NodeDetailsContent/NodeTableStyled";

jest.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key) => key,
        i18n: { changeLanguage: () => {} },
    }),
}));

describe(DateTimeEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <NuThemeProvider>
                <DateTimeEditor
                    momentFormat={"YYYY-MM-DD"}
                    readOnly={false}
                    className={""}
                    isMarked={false}
                    onValueChange={mockValueChange}
                    fieldErrors={mockFieldErrors}
                    editorFocused={false}
                    expressionObj={{ language: "spel", expression: "" }}
                    formatter={mockFormatter}
                    showValidation={true}
                />
            </NuThemeProvider>,
        );

        expect(screen.getByRole("textbox")).toHaveClass(nodeInputWithError);
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
