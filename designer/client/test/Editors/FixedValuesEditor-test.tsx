import * as React from "react";

import { render, screen } from "@testing-library/react";
import { HandledErrorType } from "../../src/components/graph/node-modal/editors/Validators";
import { jest } from "@jest/globals";
import FixedValuesEditor from "../../src/components/graph/node-modal/editors/expression/FixedValuesEditor";

jest.mock("../../src/containers/theme");

describe(FixedValuesEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <FixedValuesEditor
                readOnly={false}
                onValueChange={jest.fn()}
                validators={[
                    {
                        description: () => "HandledErrorType.EmptyMandatoryParameter",
                        handledErrorType: HandledErrorType.EmptyMandatoryParameter,
                        validatorType: 0,
                        isValid: () => false,
                        message: () => "validation error",
                    },
                ]}
                editorConfig={{
                    possibleValues: [],
                }}
                expressionObj={{ language: "spel", expression: "" }}
                showValidation={true}
                className={""}
            />,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
