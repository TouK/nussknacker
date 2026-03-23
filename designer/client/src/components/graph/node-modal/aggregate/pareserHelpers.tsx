// Aggregate-specific SpEL parsing helpers.
// Parses AGG.map() expressions and inline lists/maps.

import { extractSubstring, inlineMapToRecord, isInlineList, parseSpelToAst } from "../../../../common/spelHelpers";
import type { Ast } from "../../../../common/spelHelpers";

export function getAst(input: string, graceful = true): Ast | null {
    return parseSpelToAst(input, graceful);
}

export function printFragment(text: string, el: { start: number; end: number }): string {
    return extractSubstring(text, el);
}

function mapToList(input: string, ast: Ast | null): string[] | null {
    if (!isInlineList(ast)) return null;
    return ast.elements.map((el) => printFragment(input, el));
}

function mapToObject(input: string, ast: Ast | null): Record<string, string> | null {
    // Try to convert as InlineMap
    const result = inlineMapToRecord(input, ast);
    if (result) return result;

    // Handle AGG.map({...}) pattern
    if (
        ast?.type === "CompoundExpression" &&
        ast.expressionComponents[0]?.type === "VariableReference" &&
        ast.expressionComponents[0].variableName === "AGG" &&
        ast.expressionComponents[1]?.type === "MethodReference" &&
        ast.expressionComponents[1].methodName === "map"
    ) {
        return mapToObject(input, ast.expressionComponents[1].args[0]);
    }

    return null;
}

export function parseToList(input: string): string[] {
    return mapToList(input, getAst(input));
}

export function parseToObject<T extends Record<string, string>>(input: string): T {
    return mapToObject(input, getAst(input)) as T;
}
