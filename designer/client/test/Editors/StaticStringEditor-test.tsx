import * as React from "react";

import { render, screen } from "@testing-library/react";
import { StaticStringEditor } from "../../src/components/graph/node-modal/editors/expression/StaticStringEditor";
import { mockFieldErrors, mockFormatter, mockValueChange } from "./helpers";
import { nodeInputWithError } from "../../src/components/graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { TestProviders } from "./TestProviders";

describe(StaticStringEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <TestProviders>
                <StaticStringEditor
                    className={""}
                    onValueChange={mockValueChange}
                    expressionObj={{ language: "spel", expression: "" }}
                    formatter={mockFormatter}
                    fieldErrors={mockFieldErrors}
                    showValidation={true}
                />
            </TestProviders>,
        );

        expect(screen.getByRole("textbox")).toHaveClass(nodeInputWithError);
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
