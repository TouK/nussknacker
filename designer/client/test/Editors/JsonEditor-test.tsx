import * as React from "react";
import "ace-builds/src-noconflict/ace";

import { render, screen } from "@testing-library/react";
import { JsonEditor } from "../../src/components/graph/node-modal/editors/expression/JsonEditor";
import "ace-builds/src-noconflict/ext-language_tools";
import { mockFieldErrors, mockValueChange } from "./helpers";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";

describe(JsonEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <NuThemeProvider>
                <JsonEditor
                    onValueChange={mockValueChange}
                    fieldErrors={mockFieldErrors}
                    expressionObj={{ language: "spel", expression: "" }}
                    showValidation={true}
                    className={""}
                    fieldName={""}
                />
            </NuThemeProvider>,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
