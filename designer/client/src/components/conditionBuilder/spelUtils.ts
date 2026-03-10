// ─── Types ────────────────────────────────────────────────────────────────────

export type Operator = "==" | "!=" | "<" | "<=" | ">" | ">=" | "== null" | "!= null" | "is true" | "is false";
export type Combinator = "&&" | "||";

export interface Condition {
    id: number;
    left: string;
    operator: Operator;
    right: string;
}

export interface ParsedResult {
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
];

/** Operators that have no right-hand operand. */
export const NO_RHS_OPERATORS = new Set<Operator>(["== null", "!= null", "is true", "is false"]);

// ─── ID counter ───────────────────────────────────────────────────────────────

let _nextCondId = 1;

// ─── Utilities ────────────────────────────────────────────────────────────────

export function findOpIndex(expr: string, op: string): number {
    let depth = 0;
    for (let i = 0; i <= expr.length - op.length; i++) {
        const ch = expr[i];
        if (ch === "(" || ch === "[") {
            depth++;
            continue;
        }
        if (ch === ")" || ch === "]") {
            depth--;
            continue;
        }
        if (depth === 0 && expr.startsWith(op, i)) {
            // Don't match < inside <= or > inside >=
            if (op === "<" && expr[i + 1] === "=") continue;
            if (op === ">" && expr[i + 1] === "=") continue;
            return i;
        }
    }
    return -1;
}

function stripOuterParens(s: string): string {
    let result = s;
    while (result.startsWith("(") && result.endsWith(")")) {
        let depth = 0;
        let balanced = true;
        for (let i = 0; i < result.length - 1; i++) {
            if (result[i] === "(") depth++;
            else if (result[i] === ")") depth--;
            if (depth === 0) {
                balanced = false;
                break;
            }
        }
        if (!balanced) break;
        result = result.slice(1, -1).trim();
    }
    return result;
}

export function parseConditionPart(part: string): Omit<Condition, "id"> | null {
    const trimmed = stripOuterParens(part.trim());
    // Try operators in precedence order (longer first to avoid partial matches)
    const opsToTry: Operator[] = ["<=", ">=", "==", "!=", "<", ">"];
    for (const op of opsToTry) {
        const idx = findOpIndex(trimmed, op);
        if (idx !== -1) {
            const left = trimmed.slice(0, idx).trim();
            const rightRaw = trimmed.slice(idx + op.length).trim();
            // Check for null suffix
            if (rightRaw === "null" && op === "==") {
                return { left, operator: "== null", right: "" };
            }
            if (rightRaw === "null" && op === "!=") {
                return { left, operator: "!= null", right: "" };
            }
            return { left, operator: op as Operator, right: rightRaw };
        }
    }
    // No comparison operator — treat as boolean expression
    if (trimmed.startsWith("!")) {
        return { left: trimmed.slice(1).trim(), operator: "is false", right: "" };
    }
    if (trimmed) {
        return { left: trimmed, operator: "is true", right: "" };
    }
    return null;
}

export function parseSpel(expression: string): ParsedResult | null {
    const trimmed = expression.trim();
    if (!trimmed) return null;

    let combinator: Combinator = "&&";
    let splitOn = "";

    let depth = 0;
    let foundCombinator: Combinator | null = null;
    for (let i = 0; i < trimmed.length - 1; i++) {
        const ch = trimmed[i];
        if (ch === "(" || ch === "[") {
            depth++;
            continue;
        }
        if (ch === ")" || ch === "]") {
            depth--;
            continue;
        }
        if (depth === 0) {
            if (trimmed[i] === "&" && trimmed[i + 1] === "&") {
                foundCombinator = "&&";
                break;
            }
            if (trimmed[i] === "|" && trimmed[i + 1] === "|") {
                foundCombinator = "||";
                break;
            }
        }
    }

    if (foundCombinator) {
        combinator = foundCombinator;
        splitOn = foundCombinator;
    }

    let parts: string[];
    if (!splitOn) {
        parts = [trimmed];
    } else {
        parts = [];
        let current = "";
        let d = 0;
        for (let i = 0; i < trimmed.length; i++) {
            const ch = trimmed[i];
            if (ch === "(" || ch === "[") {
                d++;
                current += ch;
            } else if (ch === ")" || ch === "]") {
                d--;
                current += ch;
            } else if (d === 0 && trimmed.startsWith(splitOn, i)) {
                parts.push(current.trim());
                current = "";
                i += splitOn.length - 1;
            } else {
                current += ch;
            }
        }
        if (current.trim()) parts.push(current.trim());
    }

    const conditions: Omit<Condition, "id">[] = [];
    for (const part of parts) {
        const cond = parseConditionPart(part);
        if (!cond) return null;
        conditions.push(cond);
    }

    if (conditions.length === 0) return null;
    return { combinator, conditions };
}

export function genSpel(conditions: Condition[], combinator: Combinator): string {
    if (conditions.length === 0) return "";
    const parts = conditions.map((c) => {
        if (c.operator === "is true") return c.left;
        if (c.operator === "is false") return `!${c.left}`;
        if (c.operator === "== null" || c.operator === "!= null") return `${c.left} ${c.operator}`;
        return `${c.left} ${c.operator} ${c.right}`;
    });
    return parts.join(` ${combinator} `);
}

export function makeCondition(partial: Partial<Omit<Condition, "id">> = {}): Condition {
    return { id: _nextCondId++, left: "", operator: "==", right: "", ...partial };
}
