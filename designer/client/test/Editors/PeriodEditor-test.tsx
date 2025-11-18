import * as React from "react";

import { render, screen } from "@testing-library/react";
import { PeriodEditor } from "../../src/components/graph/node-modal/editors/expression/Duration/PeriodEditor";
import { TimeRange } from "../../src/components/graph/node-modal/editors/expression/Duration/TimeRangeComponent";
import { mockFieldErrors, mockFormatter, mockValueChange } from "./helpers";
import { EditorType } from "../../src/components/graph/node-modal/editors/expression/types";
import { TestProviders } from "./TestProviders";

describe(PeriodEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <TestProviders>
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
            </TestProviders>,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
