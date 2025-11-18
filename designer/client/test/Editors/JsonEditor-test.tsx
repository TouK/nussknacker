import { render, screen } from "@testing-library/react";
import * as React from "react";
import "ace-builds/src-noconflict/ace";
import { JsonEditor } from "../../src/components/graph/node-modal/editors/expression/JsonEditor";
import "ace-builds/src-noconflict/ext-language_tools";
import { mockFieldErrors, mockValueChange } from "./helpers";
import { TestProviders } from "./TestProviders";

describe("JSON Editor", () => {
    it("should display validation error when the field is required", () => {
        render(
            <TestProviders>
                <JsonEditor
                    onValueChange={mockValueChange}
                    fieldErrors={mockFieldErrors}
                    expressionObj={{ language: "spel", expression: "" }}
                    showValidation={true}
                    className={""}
                    fieldName={""}
                />
            </TestProviders>,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
