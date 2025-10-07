import * as React from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { FieldSwitch } from "../../src/components/graph/node-modal/editors/field/FieldSwitch";
import { EditorType, ExpressionLang } from "../../src/components/graph/node-modal/editors/expression/types";
import { jest } from "@jest/globals";

jest.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key) => key,
        i18n: { changeLanguage: () => {} },
    }),
}));

describe("FieldSwitch", () => {
    it.each<[string, string]>([
        ['"test"', "test"],
        ["'test'", "test"],
    ])(
        "Should verify that apostrophes at the start and end of the %s text are removed when switching from SpEL to SpELTemplate",
        (expression, expectedExpression) => {
            const mockOnValueChange = jest.fn();
            render(
                <FieldSwitch
                    expressionObj={{ expression, language: ExpressionLang.SpEL }}
                    availableEditors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }, { type: EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR }]}
                    onValueChange={mockOnValueChange}
                >
                    children
                </FieldSwitch>,
            );

            fireEvent.click(screen.getByRole("tab", { name: "string template" }));

            expect(mockOnValueChange).toHaveBeenCalledWith({
                expression: expectedExpression,
                language: "spelTemplate",
            });
        },
    );

    it.each<[string, string]>([
        ["test", '"test"'],
        ["", ""],
    ])(
        "Should verify that apostrophes at the start and end of the %s text are added when switching from SpELTemplate to SpEL",
        (expression, expectedExpression) => {
            const mockOnValueChange = jest.fn();
            render(
                <FieldSwitch
                    expressionObj={{ expression, language: ExpressionLang.SpELTemplate }}
                    availableEditors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }, { type: EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR }]}
                    onValueChange={mockOnValueChange}
                >
                    children
                </FieldSwitch>,
            );

            fireEvent.click(screen.getByRole("tab", { name: "expression" }));

            expect(mockOnValueChange).toHaveBeenCalledWith({
                expression: expectedExpression,
                language: "spel",
            });
        },
    );

    it.each<[string, boolean]>([
        ["test #{ #Base64() }", true],
        ["#{ #Base64() }", true],
        ["#{", false],
        ["#", false],
        ["#{#Base64()}", true],
    ])("should verify that expression switch field option is disabled when %s expression", (expression, isDisabled) => {
        const mockOnValueChange = jest.fn();
        render(
            <FieldSwitch
                expressionObj={{ expression, language: ExpressionLang.SpELTemplate }}
                availableEditors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }, { type: EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR }]}
                onValueChange={mockOnValueChange}
            >
                children
            </FieldSwitch>,
        );

        expect(screen.getByRole("tab", { name: "expression" })).toHaveAttribute("aria-disabled", String(isDisabled));
    });

    it.each<[string, boolean]>([
        ["#Base64()", true],
        ["#Base64", true],
        ["#", false],
        ["string literal", false],
    ])(
        "should verify that expression switch field option is disabled when expression contains %s SpEL expression",
        (expression, isDisabled) => {
            const mockOnValueChange = jest.fn();
            render(
                <FieldSwitch
                    expressionObj={{ expression, language: ExpressionLang.SpELTemplate }}
                    availableEditors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }, { type: EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR }]}
                    onValueChange={mockOnValueChange}
                >
                    children
                </FieldSwitch>,
            );

            expect(screen.getByRole("tab", { name: "string template" })).toHaveAttribute("aria-disabled", String(isDisabled));
        },
    );
});
