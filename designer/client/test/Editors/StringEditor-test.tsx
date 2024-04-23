import * as React from "react";

import { render, screen } from "@testing-library/react";
import { StringEditor } from "../../src/components/graph/node-modal/editors/expression/StringEditor";
import { mockFieldErrors, mockFormatter, mockValueChange } from "./helpers";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";
import { nodeInputWithError } from "../../src/components/graph/node-modal/NodeDetailsContent/NodeTableStyled";

describe(StringEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <NuThemeProvider>
                <StringEditor
                    className={""}
                    onValueChange={mockValueChange}
                    expressionObj={{ language: "spel", expression: "" }}
                    formatter={mockFormatter}
                    fieldErrors={mockFieldErrors}
                    showValidation={true}
                />
            </NuThemeProvider>,
        );

        expect(screen.getByRole("textbox")).toHaveClass(nodeInputWithError);
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
