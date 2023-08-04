import React from "react";

import { render, screen } from "@testing-library/react";
import { jest } from "@jest/globals";
import { StringEditor } from "../../src/components/graph/node-modal/editors/expression/StringEditor";
import { mockFormatter, mockValidators, mockValueChange } from "./helpers";

jest.mock("../../src/containers/theme");

describe(StringEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <StringEditor
                className={""}
                onValueChange={mockValueChange}
                expressionObj={{ language: "spel", expression: "" }}
                formatter={mockFormatter}
                validators={mockValidators}
                showValidation={true}
            />,
        );

        expect(screen.getByRole("textbox")).toHaveClass("node-input-with-error");
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
