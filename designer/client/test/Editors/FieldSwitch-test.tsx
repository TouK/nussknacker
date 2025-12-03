import { jest } from "@jest/globals";
import { fireEvent, render, screen } from "@testing-library/react";
import * as React from "react";
import { EditorType, ExpressionLang } from "../../src/components/graph/node-modal/editors/expression/types";
import { FieldSwitch } from "../../src/components/graph/node-modal/editors/field/FieldSwitch";
import { TestProviders } from "./TestProviders";

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
        "Should verify that quotes at the start and end of the %s text are removed when switching from SpEL to SpELTemplate",
        (expression, expectedExpression) => {
            const mockOnValueChange = jest.fn();
            render(
                <TestProviders>
                    <FieldSwitch
                        expressionObj={{ expression, language: ExpressionLang.SpEL }}
                        availableEditors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }, { type: EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR }]}
                        onValueChange={mockOnValueChange}
                    >
                        children
                    </FieldSwitch>
                </TestProviders>,
            );

            fireEvent.click(screen.getByRole("tab", { name: "string template" }));

            expect(mockOnValueChange).toHaveBeenCalledWith({
                expression: expectedExpression,
                language: "spelTemplate",
            });
        },
    );

    it.each<[string, string]>([
        ["test", "'test'"],
        ["", ""],
        ["single'quote", '"single\'quote"'],
        ["double''quote", "\"double''quote\""],
    ])(
        "Should verify that quotes at the start and end of the %s text are added when switching from SpELTemplate to SpEL",
        (expression, expectedExpression) => {
            const mockOnValueChange = jest.fn();
            render(
                <TestProviders>
                    <FieldSwitch
                        expressionObj={{ expression, language: ExpressionLang.SpELTemplate }}
                        availableEditors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }, { type: EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR }]}
                        onValueChange={mockOnValueChange}
                    >
                        children
                    </FieldSwitch>
                </TestProviders>,
            );

            fireEvent.click(screen.getByRole("tab", { name: "expression" }));

            expect(mockOnValueChange).toHaveBeenCalledWith({
                expression: expectedExpression,
                language: "spel",
            });
        },
    );

    it.each<[string, string]>([
        ["test", '"test"'],
        ["", ""],
        ["single'quote", '"single\'quote"'],
        ["double''quote", "\"double''quote\""],
    ])(
        "Should verify that quotes at the start and end of the %s text are added when switching from string template to json template editor",
        (expression, expectedExpression) => {
            const mockOnValueChange = jest.fn();
            render(
                <TestProviders>
                    <FieldSwitch
                        expressionObj={{ expression, language: ExpressionLang.SpELTemplate }}
                        availableEditors={[
                            { type: EditorType.JSON_TEMPLATE_PARAMETER_EDITOR },
                            { type: EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR },
                        ]}
                        onValueChange={mockOnValueChange}
                    >
                        children
                    </FieldSwitch>
                </TestProviders>,
            );

            fireEvent.click(screen.getByRole("tab", { name: "json template" }));

            expect(mockOnValueChange).toHaveBeenCalledWith({
                expression: expectedExpression,
                language: "jsonTemplate",
            });
        },
    );

    it.each<[string, boolean]>([
        ["test #{ #Base64() }", true],
        ["#{ #Base64() }", true],
        ["#{", false],
        ["#", false],
        ["#{#Base64()}", true],
        ["", false],
    ])("should verify that expression switch field option is disabled when %s expression", (expression, isDisabled) => {
        const mockOnValueChange = jest.fn();
        render(
            <TestProviders>
                <FieldSwitch
                    expressionObj={{ expression, language: ExpressionLang.SpELTemplate }}
                    availableEditors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }, { type: EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR }]}
                    onValueChange={mockOnValueChange}
                >
                    children
                </FieldSwitch>
            </TestProviders>,
        );

        expect(screen.getByRole("tab", { name: "expression" })).toHaveAttribute("aria-disabled", String(isDisabled));
    });

    it.each<[string, boolean]>([
        ["#Base64()", true],
        ["#Base64", true],
        ["#", true],
        ["'string literal'", false],
        ["a" + "b", true],
        [`#DATE.now.toString + 'millis'`, true],
        ["", false],
    ])(
        "should verify that string template switch field option is disabled when expression contains %s SpEL expression and expression editor configured",
        (expression, isDisabled) => {
            const mockOnValueChange = jest.fn();
            render(
                <TestProviders>
                    <FieldSwitch
                        expressionObj={{ expression, language: ExpressionLang.SpEL }}
                        availableEditors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }, { type: EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR }]}
                        onValueChange={mockOnValueChange}
                    >
                        children
                    </FieldSwitch>
                </TestProviders>,
            );

            expect(screen.getByRole("tab", { name: "string template" })).toHaveAttribute("aria-disabled", String(isDisabled));
        },
    );

    it.each<[string, boolean]>([
        [
            "{\n" +
                '  "name": "#{ #input.name }",\n' +
                '  "age": #{ #input.age },\n' +
                "  #{ #input.secondName ?: ', \"secondName\": \"' + #input.secondName + '\"' }\n" +
                "}",
            true,
        ],
        ["'string literal'", false],
        ["a" + "b", true],
        ["", false],
        ["32", true],
        ["true", true],
    ])(
        "should verify that string template switch field option is disabled when expression contains %s SpEL expression and json template editor configured",
        (expression, isDisabled) => {
            const mockOnValueChange = jest.fn();
            render(
                <TestProviders>
                    <FieldSwitch
                        expressionObj={{ expression, language: ExpressionLang.JsonTemplate }}
                        availableEditors={[
                            { type: EditorType.JSON_TEMPLATE_PARAMETER_EDITOR },
                            { type: EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR },
                        ]}
                        onValueChange={mockOnValueChange}
                    >
                        children
                    </FieldSwitch>
                </TestProviders>,
            );

            expect(screen.getByRole("tab", { name: "string template" })).toHaveAttribute("aria-disabled", String(isDisabled));
        },
    );
});
