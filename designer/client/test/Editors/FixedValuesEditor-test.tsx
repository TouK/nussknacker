import { render, screen } from "@testing-library/react";
import * as React from "react";
import { FixedValuesEditor } from "../../src/components/graph/node-modal/editors/expression/FixedValuesEditor";
import { EditorType } from "../../src/components/graph/node-modal/editors/expression/types";
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
                        type: EditorType.FIXED_VALUES_PARAMETER_EDITOR,
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
