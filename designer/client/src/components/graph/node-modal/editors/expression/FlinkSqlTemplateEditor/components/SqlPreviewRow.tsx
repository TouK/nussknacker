import CheckIcon from "@mui/icons-material/Check";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import { Box, IconButton, Tooltip } from "@mui/material";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

import { FormRow } from "./FormRow";

// ── Tokenizer ──────────────────────────────────────────────────────────────

type TokenType = "keyword" | "number" | "comment" | "plain";

const KEYWORDS = new Set(
    (
        "SELECT FROM WHERE AS AND OR NOT PARTITION ORDER BY ROW_NUMBER OVER " +
        "MATCH_RECOGNIZE MEASURES DEFINE PATTERN AFTER MATCH SKIP PAST LAST ROW " +
        "FIRST NEXT ONE ALL ROWS PER INTERVAL TUMBLE HOP CUMULATE DESCRIPTOR TABLE " +
        "WITH IN IS NULL TRUE FALSE ASC DESC CASE WHEN THEN ELSE END"
    ).split(" "),
);

const PATTERNS: Array<{ type: TokenType; re: RegExp }> = [
    { type: "comment", re: /^--[^\n]*/ },
    { type: "number", re: /^\b\d+\b/ },
    { type: "keyword", re: /^[A-Za-z_]\w*/ },
    { type: "plain", re: /^[\s\S]/ },
];

function tokenize(sql: string): Array<{ type: TokenType; value: string }> {
    const tokens: Array<{ type: TokenType; value: string }> = [];
    let rest = sql;
    while (rest.length > 0) {
        for (const { type, re } of PATTERNS) {
            const m = re.exec(rest);
            if (!m) continue;
            const value = m[0];
            const actualType: TokenType = type === "keyword" ? (KEYWORDS.has(value.toUpperCase()) ? "keyword" : "plain") : type;
            if (actualType === "plain" && tokens.at(-1)?.type === "plain") {
                tokens[tokens.length - 1].value += value;
            } else {
                tokens.push({ type: actualType, value });
            }
            rest = rest.slice(value.length);
            break;
        }
    }
    return tokens;
}

const TOKEN_COLORS: Partial<Record<TokenType, string>> = {
    keyword: "#e5c07b",
    number: "#d19a66",
    comment: "#7f848e",
};

function HighlightedSql({ sql }: { sql: string }) {
    return (
        <>
            {tokenize(sql).map((tok, i) =>
                TOKEN_COLORS[tok.type] ? (
                    <span key={i} style={{ color: TOKEN_COLORS[tok.type] }}>
                        {tok.value}
                    </span>
                ) : (
                    <React.Fragment key={i}>{tok.value}</React.Fragment>
                ),
            )}
        </>
    );
}

// ── Component ──────────────────────────────────────────────────────────────

export function SqlPreviewRow({ sql }: { sql: string }) {
    const { t } = useTranslation();
    const [pinned, setPinned] = useState(false);
    const [copied, setCopied] = useState(false);

    const handleCopy = (e: React.MouseEvent) => {
        e.stopPropagation();
        navigator.clipboard.writeText(sql).catch(() => undefined);
        setCopied(true);
        setTimeout(() => setCopied(false), 1500);
    };

    return (
        <FormRow label={t("flinkSql.sqlPreview.label", "Generated SQL:")} alignItems="center">
            <Box sx={{ position: "relative" }}>
                <Tooltip
                    title={
                        !pinned ? (
                            <Box
                                component="pre"
                                sx={{ m: 0, fontSize: "0.72rem", fontFamily: "monospace", whiteSpace: "pre", color: "#abb2bf" }}
                            >
                                <HighlightedSql sql={sql} />
                            </Box>
                        ) : (
                            ""
                        )
                    }
                    placement="top"
                    arrow
                    slotProps={{ tooltip: { sx: { maxWidth: 640 } } }}
                >
                    <Box
                        component="pre"
                        onClick={() => setPinned((p) => !p)}
                        sx={{
                            m: 0,
                            px: 1.5,
                            py: 0.75,
                            pr: pinned ? 5 : 1.5,
                            fontSize: "0.72rem",
                            fontFamily: "monospace",
                            lineHeight: 1.6,
                            backgroundColor: "rgba(0,0,0,0.25)",
                            color: "#abb2bf",
                            border: "1px solid",
                            borderColor: "divider",
                            cursor: "pointer",
                            transition: "max-height 0.25s ease",
                            maxHeight: pinned ? "300px" : "2rem",
                            whiteSpace: pinned ? "pre" : "nowrap",
                            overflow: pinned ? "auto" : "hidden",
                            textOverflow: pinned ? "clip" : "ellipsis",
                        }}
                    >
                        <HighlightedSql sql={sql} />
                    </Box>
                </Tooltip>

                {/* Copy button — only when pinned open */}
                {pinned && (
                    <Tooltip title={copied ? t("common.copied", "Copied!") : t("common.copy", "Copy")}>
                        <IconButton
                            size="small"
                            onClick={handleCopy}
                            sx={{ position: "absolute", top: 4, right: 4, opacity: 0.5, "&:hover": { opacity: 1 } }}
                        >
                            {copied ? <CheckIcon sx={{ fontSize: "0.85rem" }} /> : <ContentCopyIcon sx={{ fontSize: "0.85rem" }} />}
                        </IconButton>
                    </Tooltip>
                )}
            </Box>
        </FormRow>
    );
}
