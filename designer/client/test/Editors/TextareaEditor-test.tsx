import * as React from "react";

import { render, screen } from "@testing-library/react";
import { TextareaEditor } from "../../src/components/graph/node-modal/editors/expression/TextareaEditor";
import { mockFieldErrors, mockFormatter } from "./helpers";
import { nodeInputWithError } from "../../src/components/graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { TestProviders } from "./TestProviders";

describe(TextareaEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <TestProviders>
                <TextareaEditor
                    className={""}
                    onValueChange={jest.fn()}
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
