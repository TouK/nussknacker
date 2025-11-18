import * as React from "react";

import { render, screen } from "@testing-library/react";
import { jest } from "@jest/globals";
import { DateEditor } from "../../src/components/graph/node-modal/editors/expression/DateTimeEditor/DateEditor";
import { mockFieldErrors, mockFormatter, mockValueChange } from "./helpers";
import { nodeInputWithError } from "../../src/components/graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { TestProviders } from "./TestProviders";

jest.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key) => key,
        i18n: { changeLanguage: () => {} },
    }),
}));

describe(DateEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <TestProviders>
                <DateEditor
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
            </TestProviders>,
        );

        expect(screen.getByRole("textbox")).toHaveClass(nodeInputWithError);
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
