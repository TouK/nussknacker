import * as React from "react";

import { render, screen } from "@testing-library/react";
import { jest } from "@jest/globals";
import { FixedValuesEditor } from "src/components/graph/node-modal/editors/expression/FixedValuesEditor";
import { mockFieldError, mockValueChange } from "./helpers";

jest.mock("../../src/containers/theme");

describe(FixedValuesEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <FixedValuesEditor
                readOnly={false}
                onValueChange={mockValueChange}
                fieldErrors={mockFieldError}
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
