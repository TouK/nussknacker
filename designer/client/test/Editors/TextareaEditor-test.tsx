import * as React from "react";

import { render, screen } from "@testing-library/react";
import { TextareaEditor } from "../../src/components/graph/node-modal/editors/expression/TextareaEditor";
import { mockErrors, mockFormatter } from "./helpers";

jest.mock("../../src/containers/theme");

describe(TextareaEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <TextareaEditor
                className={""}
                onValueChange={jest.fn()}
                expressionObj={{ language: "spel", expression: "" }}
                formatter={mockFormatter}
                fieldErrors={mockErrors}
                showValidation={true}
            />,
        );

        expect(screen.getByRole("textbox")).toHaveClass("node-input-with-error");
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
