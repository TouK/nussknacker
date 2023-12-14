import * as React from "react";

import { render, screen } from "@testing-library/react";
import { jest } from "@jest/globals";
import { TimeEditor } from "../../src/components/graph/node-modal/editors/expression/DateTimeEditor";
import { mockFieldErrors, mockFormatter, mockValueChange } from "./helpers";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";

jest.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key) => key,
        i18n: { changeLanguage: () => {} },
    }),
}));

describe(TimeEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <NuThemeProvider>
                <TimeEditor
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

        expect(screen.getByRole("textbox")).toHaveClass("node-input-with-error");
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
