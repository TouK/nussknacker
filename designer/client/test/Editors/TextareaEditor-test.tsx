import * as React from "react";

import { render, screen } from "@testing-library/react";
import { TextareaEditor } from "../../src/components/graph/node-modal/editors/expression/TextareaEditor";
import { mockFieldErrors, mockFormatter } from "./helpers";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";

describe(TextareaEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <NuThemeProvider>
                <TextareaEditor
                    className={""}
                    onValueChange={jest.fn()}
                    expressionObj={{ language: "spel", expression: "" }}
                    formatter={mockFormatter}
                    fieldErrors={mockFieldErrors}
                    showValidation={true}
                />
            </NuThemeProvider>,
        );

        expect(screen.getByRole("textbox")).toHaveClass("node-input-with-error");
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
