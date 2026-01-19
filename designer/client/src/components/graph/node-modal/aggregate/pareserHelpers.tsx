import { parse } from "ts-spel";
import type { Ast } from "ts-spel/lib/lib/Ast";

export function getAst(input: string) {
    try {
        return parse(input, true);
    } catch (e) {
        return null;
    }
}

function printFragment(text: string, el: { start: number; end: number }): string {
    if (!text || !el) return "";
    return text.slice(el.start, el.end).trim();
}

function mapToList(input: string, ast?: Ast): string[] | null {
    if (ast?.type !== "InlineList" || ast.__unclosed) return null;
    return ast.elements.map((el) => printFragment(input, el));
}

function mapToObject(input: string, ast?: Ast): Record<string, string> | null {
    switch (ast?.type) {
        case "InlineMap":
            return Object.fromEntries(Object.entries(ast.elements).map(([key, el]) => [key, printFragment(input, el)]));
        case "CompoundExpression":
            if (ast.expressionComponents[0].type !== "VariableReference") return null;
            if (ast.expressionComponents[0].variableName !== "AGG") return null;
            if (ast.expressionComponents[1].type !== "MethodReference") return null;
            if (ast.expressionComponents[1].methodName !== "map") return null;
            return mapToObject(input, ast.expressionComponents[1].args[0]);
        default:
            return null;
    }
}

export function parseToList(input: string): string[] {
    return mapToList(input, getAst(input));
}

export function parseToObject<T extends Record<string, string>>(input: string): T {
    return mapToObject(input, getAst(input)) as T;
}
