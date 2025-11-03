import * as React from "react";

import { render, screen } from "@testing-library/react";
import { FixedValuesEditor } from "../../src/components/graph/node-modal/editors/expression/FixedValuesEditor";
import { mockFieldErrors, mockValueChange } from "./helpers";
import { TestProviders } from "./TestProviders";

describe(FixedValuesEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <TestProviders>
                <FixedValuesEditor
                    readOnly={false}
                    onValueChange={mockValueChange}
                    fieldErrors={mockFieldErrors}
                    editorConfig={{
                        possibleValues: [],
                    }}
                    expressionObj={{ language: "spel", expression: "" }}
                    showValidation={true}
                    className={""}
                />
            </TestProviders>,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
