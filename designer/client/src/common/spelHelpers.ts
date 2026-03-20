// Common helpers for working with SpEL (Spring Expression Language).
// This is the single point of contact with the ts-spel library.

import { parse, prettyPrint } from "ts-spel";
import type { Ast } from "ts-spel/lib/lib/Ast";

// ─── Core Parsing ─────────────────────────────────────────────────────────────

/**
 * Parse SpEL expression to AST.
 * Trims input and returns null for empty strings.
 * Returns null on parse error instead of throwing.
 */
export function parseSpelToAst(input: string, graceful = true): Ast | null {
    const trimmed = input.trim();
    if (!trimmed) return null;

    try {
        return parse(trimmed, graceful);
    } catch {
        return null;
    }
}

/**
 * Convert AST back to SpEL string.
 */
export function astToSpelString(ast: Ast): string {
    return prettyPrint(ast);
}

// ─── Substring Extraction ─────────────────────────────────────────────────────

type NodeWithRange = { start: number; end: number };
type AstWithOptionalRange = Ast & { start?: number; end?: number };

/**
 * Extract substring from original input based on start/end positions.
 * Can accept either:
 * - AST node with optional start/end positions (falls back to prettyPrint)
 * - Plain object with start/end positions
 */
export function extractSubstring(input: string, node: NodeWithRange | AstWithOptionalRange): string {
    if ("start" in node && "end" in node && node.start != null && node.end != null) {
        return input.slice(node.start, node.end).trim();
    }

    // If it's an AST node without positions, use prettyPrint
    if ("type" in node) {
        return prettyPrint(node as Ast).trim();
    }

    return "";
}

// ─── AST Construction Helpers ─────────────────────────────────────────────────

/**
 * Create a StringLiteral AST node.
 */
export function createStringLiteral(value: string): Ast {
    return { type: "StringLiteral", value };
}

// ─── Type Guards ──────────────────────────────────────────────────────────────

export function isInlineList(ast: Ast | null): ast is Ast & { type: "InlineList"; elements: Array<Ast & { start: number; end: number }> } {
    return ast?.type === "InlineList" && !("__unclosed" in ast && ast.__unclosed);
}

export function isInlineMap(
    ast: Ast | null,
): ast is Ast & { type: "InlineMap"; elements: Record<string, Ast & { start: number; end: number }> } {
    return ast?.type === "InlineMap";
}

export function isStringLiteral(ast: Ast | null): ast is Ast & { type: "StringLiteral"; value: string } {
    return ast?.type === "StringLiteral";
}

// ─── InlineMap Utilities ──────────────────────────────────────────────────────

/**
 * Convert InlineMap AST node to Record<string, string> by extracting element values from original input.
 * Returns null if ast is not an InlineMap.
 */
export function inlineMapToRecord(input: string, ast: Ast | null): Record<string, string> | null {
    if (!isInlineMap(ast)) return null;
    return Object.fromEntries(Object.entries(ast.elements).map(([key, el]) => [key, extractSubstring(input, el)]));
}

// ─── Re-export types ──────────────────────────────────────────────────────────

export type { Ast };
