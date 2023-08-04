import * as React from "react";

import { render, screen } from "@testing-library/react";
import { jest } from "@jest/globals";
import { DateEditor } from "../../src/components/graph/node-modal/editors/expression/DateTimeEditor";
import { mockFormatter, mockValidators, mockValueChange } from "./helpers";

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
                onValueChange={mockValueChange}
                validators={mockValidators}
                editorFocused={false}
                expressionObj={{ language: "spel", expression: "" }}
                formatter={mockFormatter}
                showValidation={true}
            />,
        );

        expect(screen.getByRole("textbox")).toHaveClass("node-input-with-error");
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
