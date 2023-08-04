import * as React from "react";

import { render, screen } from "@testing-library/react";
import { jest } from "@jest/globals";
import { DualParameterEditor } from "../../src/components/graph/node-modal/editors/expression/DualParameterEditor";
import { DualEditorMode, EditorType } from "../../src/components/graph/node-modal/editors/expression/Editor";
import { mockValidators, mockValueChange } from "./helpers";

jest.mock("../../src/containers/theme");

describe(DualParameterEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <DualParameterEditor
                readOnly={false}
                className={""}
                isMarked={false}
                onValueChange={mockValueChange}
                validators={mockValidators}
                editorConfig={{
                    simpleEditor: { type: EditorType.CRON_EDITOR },
                    defaultMode: DualEditorMode.SIMPLE,
                }}
                expressionObj={{ language: "spel", expression: "" }}
                showValidation={true}
                variableTypes={{}}
            />,
        );

        expect(screen.getByRole("textbox")).toHaveClass("node-input-with-error");
        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
