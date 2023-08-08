import * as React from "react";

import { CronEditor } from "../../src/components/graph/node-modal/editors/expression/Cron/CronEditor";
import { render, screen } from "@testing-library/react";
import { jest } from "@jest/globals";
import { mockErrors, mockFormatter, mockValueChange } from "./helpers";

jest.mock("../../src/containers/theme");

describe(CronEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <CronEditor
                readOnly={false}
                className={""}
                isMarked={false}
                onValueChange={mockValueChange}
                fieldErrors={mockErrors}
                expressionObj={{ language: "spel", expression: "" }}
                formatter={mockFormatter}
                showValidation={true}
            />,
        );

        expect(screen.getByRole("textbox")).toHaveClass("node-input-with-error");
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
