import * as React from "react";
import "ace-builds/src-noconflict/ace";

import { render, screen } from "@testing-library/react";
import { HandledErrorType } from "../../src/components/graph/node-modal/editors/Validators";
import { jest } from "@jest/globals";
import JsonEditor from "../../src/components/graph/node-modal/editors/expression/JsonEditor";
import "ace-builds/src-noconflict/ext-language_tools";

jest.mock("../../src/containers/theme");

describe(JsonEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <JsonEditor
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
                expressionObj={{ language: "spel", expression: "" }}
                showValidation={true}
                className={""}
            />,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
