import * as React from "react";

import { render, screen } from "@testing-library/react";
import { PeriodEditor } from "../../src/components/graph/node-modal/editors/expression/Duration/PeriodEditor";
import { TimeRange } from "../../src/components/graph/node-modal/editors/expression/Duration/TimeRangeComponent";
import { mockFormatter, mockFieldErrors, mockValueChange } from "./helpers";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";
import { EditorType } from "../../src/components/graph/node-modal/editors/expression/types";

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
                        type: EditorType.CRON_EDITOR,
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
