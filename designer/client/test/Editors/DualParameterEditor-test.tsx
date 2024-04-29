import * as React from "react";

import { render, screen } from "@testing-library/react";
import { DualParameterEditor } from "../../src/components/graph/node-modal/editors/expression/DualParameterEditor";
import { DualEditorMode, EditorType } from "../../src/components/graph/node-modal/editors/expression/Editor";
import { mockFieldErrors, mockValueChange } from "./helpers";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";
import { nodeInputWithError } from "../../src/components/graph/node-modal/NodeDetailsContent/NodeTableStyled";

describe(DualParameterEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <NuThemeProvider>
                <DualParameterEditor
                    readOnly={false}
                    className={""}
                    isMarked={false}
                    onValueChange={mockValueChange}
                    fieldErrors={mockFieldErrors}
                    editorConfig={{
                        simpleEditor: { type: EditorType.CRON_EDITOR },
                        defaultMode: DualEditorMode.SIMPLE,
                    }}
                    expressionObj={{ language: "spel", expression: "" }}
                    showValidation={true}
                    variableTypes={{}}
                />
            </NuThemeProvider>,
        );

        expect(screen.getByRole("textbox")).toHaveClass(nodeInputWithError);
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
