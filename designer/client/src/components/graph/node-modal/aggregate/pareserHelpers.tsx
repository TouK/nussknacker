import { parse } from "ts-spel";
import { Ast } from "ts-spel/lib/lib/Ast";

function getAst(input: string) {
    try {
        return parse(input, true);
    } catch (e) {
        return null;
    }
}

function printFragment(text: string, el: { start: number; end: number }): string {
    return text.slice(el.start, el.end).trim();
}

function mapToList(input: string, ast?: Ast): string[] | null {
    if (ast?.type !== "CompoundExpression") return null;
    if (ast.expressionComponents[0].type !== "InlineList") return null;
    if (ast.expressionComponents[1].type !== "PropertyReference") return null;
    if (ast.expressionComponents[1].propertyName !== "toString") return null;
    return ast.expressionComponents[0].elements.map((el) => printFragment(input, el));
}

function mapToObject(input: string, ast?: Ast): Record<string, string> | null {
    switch (ast?.type) {
        case "InlineList":
            return ast.elements.length < 1 ? {} : null;
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

export function parseToObject(input: string): Record<string, string> {
    return mapToObject(input, getAst(input));
}
