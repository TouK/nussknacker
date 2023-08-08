import * as React from "react";

import { render, screen } from "@testing-library/react";
import { jest } from "@jest/globals";
import { DualEditorMode, EditorType } from "../../src/components/graph/node-modal/editors/expression/Editor";
import { DurationEditor } from "../../src/components/graph/node-modal/editors/expression/Duration/DurationEditor";
import { TimeRange } from "../../src/components/graph/node-modal/editors/expression/Duration/TimeRangeComponent";
import { mockFormatter, mockErrors, mockValueChange } from "./helpers";

jest.mock("../../src/containers/theme");

describe(DurationEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <DurationEditor
                readOnly={false}
                isMarked={false}
                onValueChange={mockValueChange}
                fieldErrors={mockErrors}
                editorConfig={{
                    simpleEditor: { type: EditorType.CRON_EDITOR },
                    defaultMode: DualEditorMode.SIMPLE,
                    timeRangeComponents: [TimeRange.Years],
                }}
                expressionObj={{ language: "spel", expression: "" }}
                showValidation={true}
                formatter={mockFormatter}
            />,
        );

        expect(screen.getByRole("spinbutton")).toHaveClass("node-input-with-error");
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
