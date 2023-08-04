import * as React from "react";
import "ace-builds/src-noconflict/ace";

import { render, screen } from "@testing-library/react";
import { jest } from "@jest/globals";
import { JsonEditor } from "../../src/components/graph/node-modal/editors/expression/JsonEditor";
import "ace-builds/src-noconflict/ext-language_tools";
import { mockValidators, mockValueChange } from "./helpers";

jest.mock("../../src/containers/theme");

describe(JsonEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <JsonEditor
                onValueChange={mockValueChange}
                validators={mockValidators}
                expressionObj={{ language: "spel", expression: "" }}
                showValidation={true}
                className={""}
            />,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});
