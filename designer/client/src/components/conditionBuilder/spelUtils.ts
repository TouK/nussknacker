import type { Ast } from "../../common/spelHelpers";
import { astToSpelString, isStringLiteral, parseSpelToAst } from "../../common/spelHelpers";

// ─── Types ────────────────────────────────────────────────────────────────────

type Operator = "==" | "!=" | "<" | "<=" | ">" | ">=" | "== null" | "!= null" | "is true" | "is false" | "matches" | "not matches";
export type Combinator = "&&" | "||";

export interface Condition {
    id: number;
    left: string;
    operator: Operator;
    right: string;
}

interface ParsedResult {
    combinator: Combinator;
    conditions: Omit<Condition, "id">[];
}

// ─── Constants ────────────────────────────────────────────────────────────────

export const OPERATORS: { value: Operator; label: string }[] = [
    { value: "is true", label: "is true" },
    { value: "is false", label: "is false" },
    { value: "==", label: "equals" },
    { value: "!=", label: "not equals" },
    { value: "<", label: "less than" },
    { value: "<=", label: "less than or equal" },
    { value: ">", label: "greater than" },
    { value: ">=", label: "greater than or equal" },
    { value: "== null", label: "is null" },
    { value: "!= null", label: "is not null" },
    { value: "matches", label: "matches (regex)" },
    { value: "not matches", label: "not matches (regex)" },
];

/** Operators that have no right-hand operand. */
export const NO_RHS_OPERATORS = new Set<Operator>(["== null", "!= null", "is true", "is false"]);

/** Operators whose right-hand side is a plain regex string (not a SpEL expression). */
export const REGEX_OPERATORS = new Set<Operator>(["matches", "not matches"]);

// ─── ID counter ───────────────────────────────────────────────────────────────

let _nextCondId = 1;

// ─── AST helpers ─────────────────────────────────────────────────────────────

/** Flatten a left-associative binary tree (OpAnd / OpOr) into leaf nodes. Returns null if the tree contains null children. */
function flattenBinary(ast: Ast | null, type: "OpAnd" | "OpOr"): Ast[] | null {
    if (!ast) return null;
    if (ast.type === type) {
        const left = flattenBinary(ast.left, type);
        const right = flattenBinary(ast.right, type);
        if (!left || !right) return null;
        return [...left, ...right];
    }
    return [ast];
}

// ─── Condition parsing ────────────────────────────────────────────────────────

function parseConditionAst(ast: Ast): Omit<Condition, "id"> | null {
    // not matches: !(left matches pattern)
    if (ast.type === "OpNot" && ast.expression.type === "OpMatches") {
        const inner = ast.expression;
        const left = astToSpelString(inner.left);
        const right = isStringLiteral(inner.right) ? inner.right.value : astToSpelString(inner.right);
        return { left, operator: "not matches", right };
    }

    // is false: !expr
    if (ast.type === "OpNot") {
        return { left: astToSpelString(ast.expression), operator: "is false", right: "" };
    }

    // matches: left matches pattern
    if (ast.type === "OpMatches") {
        const left = astToSpelString(ast.left);
        const right = isStringLiteral(ast.right) ? ast.right.value : astToSpelString(ast.right);
        return { left, operator: "matches", right };
    }

    // == null / != null
    if (ast.type === "OpEQ" && ast.right.type === "NullLiteral") {
        return { left: astToSpelString(ast.left), operator: "== null", right: "" };
    }
    if (ast.type === "OpNE" && ast.right.type === "NullLiteral") {
        return { left: astToSpelString(ast.left), operator: "!= null", right: "" };
    }

    // Binary comparison operators
    if (ast.type === "OpEQ") return { left: astToSpelString(ast.left), operator: "==", right: astToSpelString(ast.right) };
    if (ast.type === "OpNE") return { left: astToSpelString(ast.left), operator: "!=", right: astToSpelString(ast.right) };
    if (ast.type === "OpLT") return { left: astToSpelString(ast.left), operator: "<", right: astToSpelString(ast.right) };
    if (ast.type === "OpLE") return { left: astToSpelString(ast.left), operator: "<=", right: astToSpelString(ast.right) };
    if (ast.type === "OpGT") return { left: astToSpelString(ast.left), operator: ">", right: astToSpelString(ast.right) };
    if (ast.type === "OpGE") return { left: astToSpelString(ast.left), operator: ">=", right: astToSpelString(ast.right) };

    // is true: any non-comparison expression
    return { left: astToSpelString(ast), operator: "is true", right: "" };
}

// ─── Public API ───────────────────────────────────────────────────────────────

export function parseSpel(expression: string): ParsedResult | null {
    const ast = parseSpelToAst(expression);
    if (!ast) return null;

    let combinator: Combinator = "&&";
    let conditionAsts: Ast[] | null;

    if (ast.type === "OpAnd") {
        combinator = "&&";
        conditionAsts = flattenBinary(ast, "OpAnd");
    } else if (ast.type === "OpOr") {
        combinator = "||";
        conditionAsts = flattenBinary(ast, "OpOr");
    } else {
        conditionAsts = [ast];
    }

    if (!conditionAsts) return null;

    const conditions: Omit<Condition, "id">[] = [];
    for (const node of conditionAsts) {
        const cond = parseConditionAst(node);
        if (!cond) return null;
        conditions.push(cond);
    }

    if (conditions.length === 0) return null;
    return { combinator, conditions };
}

export function genSpel(conditions: Omit<Condition, "id">[], combinator: Combinator): string {
    if (conditions.length === 0) return "";
    const parts = conditions.map((c) => {
        if (c.operator === "is true") return c.left;
        if (c.operator === "is false") return `!${c.left}`;
        if (c.operator === "== null" || c.operator === "!= null") return `${c.left} ${c.operator}`;
        if (c.operator === "matches") return `${c.left} matches '${c.right}'`;
        if (c.operator === "not matches") return `!(${c.left} matches '${c.right}')`;
        return `${c.left} ${c.operator} ${c.right}`;
    });
    return parts.length === 1 ? parts[0] : parts.join(`\n${combinator} `);
}

export function makeCondition(partial: Partial<Omit<Condition, "id">> = {}): Condition {
    return { id: _nextCondId++, left: "", operator: "==", right: "", ...partial };
}
