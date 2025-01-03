import * as React from "react";

import { render, screen } from "@testing-library/react";
import { PeriodEditor } from "../../src/components/graph/node-modal/editors/expression/Duration/PeriodEditor";
import { DualEditorMode, EditorType } from "../../src/components/graph/node-modal/editors/expression/Editor";
import { TimeRange } from "../../src/components/graph/node-modal/editors/expression/Duration/TimeRangeComponent";
import { mockFormatter, mockFieldErrors, mockValueChange } from "./helpers";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";
import { nodeInputWithError } from "../../src/components/graph/node-modal/NodeDetailsContent/NodeTableStyled";

describe(PeriodEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <NuThemeProvider>
                <PeriodEditor
                    readOnly={false}
                    isMarked={false}
                    onValueChange={mockValueChange}
                    fieldErrors={mockFieldErrors}
                    editorConfig={{
                        simpleEditor: { type: EditorType.CRON_EDITOR },
                        defaultMode: DualEditorMode.SIMPLE,
                        timeRangeComponents: [TimeRange.Years],
                    }}
                    expressionObj={{ language: "spel", expression: "" }}
                    showValidation={true}
                    formatter={mockFormatter}
                />
            </NuThemeProvider>,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
