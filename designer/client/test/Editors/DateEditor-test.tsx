import * as React from "react";

import { render, screen } from "@testing-library/react";
import { HandledErrorType } from "../../src/components/graph/node-modal/editors/Validators";
import { jest } from "@jest/globals";
import { DateEditor } from "../../src/components/graph/node-modal/editors/expression/DateTimeEditor";

jest.mock("../../src/containers/theme");
jest.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key) => key,
        i18n: { changeLanguage: () => {} },
    }),
}));

describe(DateEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <DateEditor
                momentFormat={"YYYY-MM-DD"}
                readOnly={false}
                className={""}
                isMarked={false}
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
                editorFocused={false}
                expressionObj={{ language: "spel", expression: "" }}
                formatter={{ encode: jest.fn(() => "test"), decode: jest.fn() }}
                showValidation={true}
            />,
        );

        expect(screen.getByRole("textbox")).toHaveClass("node-input-with-error");
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
