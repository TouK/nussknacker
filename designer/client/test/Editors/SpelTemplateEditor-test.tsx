import { render, screen } from "@testing-library/react";
import "ace-builds/src-noconflict/ace";
import "ace-builds/src-noconflict/ext-language_tools";
import * as React from "react";

import { SpelTemplateEditor } from "../../src/components/graph/node-modal/editors/expression/SpelTemplateEditor";
import { mockFieldErrors, mockValueChange } from "./helpers";
import { TestProviders } from "./TestProviders";

describe("SpelTemplateEditor", () => {
    it("should display validation error when the field is required", () => {
        render(
            <TestProviders>
                <SpelTemplateEditor
                    readOnly={false}
                    isMarked={false}
                    onValueChange={mockValueChange}
                    fieldErrors={mockFieldErrors}
                    expressionObj={{ language: "spel", expression: "" }}
                    showValidation={true}
                    className={""}
                    variableTypes={{}}
                />
            </TestProviders>,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
