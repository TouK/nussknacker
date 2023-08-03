import React from "react";

import { render, screen } from "@testing-library/react";
import { jest } from "@jest/globals";
import StringEditor from "../../src/components/graph/node-modal/editors/expression/StringEditor";
import { HandledErrorType } from "../../src/components/graph/node-modal/editors/Validators";

jest.mock("../../src/containers/theme");

describe(StringEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <StringEditor
                className={""}
                onValueChange={jest.fn()}
                expressionObj={{ language: "spel", expression: "" }}
                formatter={{ encode: jest.fn(() => "test"), decode: jest.fn() }}
                validators={[
                    {
                        description: () => "HandledErrorType.EmptyMandatoryParameter",
                        handledErrorType: HandledErrorType.EmptyMandatoryParameter,
                        validatorType: 0,
                        isValid: () => false,
                        message: () => "validation error",
                    },
                ]}
                showValidation={true}
            />,
        );

        expect(screen.getByRole("textbox")).toHaveClass("node-input-with-error");
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
