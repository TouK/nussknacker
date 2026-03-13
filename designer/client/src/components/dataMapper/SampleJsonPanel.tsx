import { Box, Button, styled, TextField, Typography } from "@mui/material";
import React, { useState } from "react";

// ─── Styled ───────────────────────────────────────────────────────────────────

export const SamplePanel = styled(Box)(({ theme }) => ({
    padding: theme.spacing(1.5, 2),
    borderBottom: `1px solid ${theme.palette.divider}`,
    backgroundColor: theme.palette.background.default,
}));

// ─── Props ────────────────────────────────────────────────────────────────────

interface SampleJsonPanelProps {
    title: string;
    placeholder: string;
    mergeLabel?: string;
    onApply: (parsed: unknown, mode: "replace" | "merge") => string | null;
    onClose: () => void;
}

// ─── Component ────────────────────────────────────────────────────────────────

export function SampleJsonPanel({ title, placeholder, mergeLabel, onApply, onClose }: SampleJsonPanelProps): React.JSX.Element {
    const [text, setText] = useState("");
    const [error, setError] = useState("");
    const [mode, setMode] = useState<"replace" | "merge">("replace");

    const handleApply = () => {
        const trimmed = text.trim();
        if (!trimmed) {
            setError("Paste a JSON object first");
            return;
        }
        let parsed: unknown;
        try {
            parsed = JSON.parse(trimmed);
        } catch (e) {
            setError(`Invalid JSON: ${(e as Error).message}`);
            return;
        }
        const err = onApply(parsed, mode);
        if (err) {
            setError(err);
            return;
        }
        setError("");
    };

    return (
        <SamplePanel>
            <Typography sx={{ fontSize: 12, mb: 1 }}>{title}</Typography>
            <TextField
                value={text}
                onChange={(e) => {
                    setText(e.target.value);
                    setError("");
                }}
                placeholder={placeholder}
                multiline
                minRows={5}
                fullWidth
                size="small"
                sx={{ mb: 1, "& .MuiInputBase-input": { fontSize: 12, fontFamily: "monospace" } }}
            />
            {error && <Typography sx={{ fontSize: 11, color: "error.light", mb: 1 }}>{error}</Typography>}
            <Box sx={{ display: "flex", gap: 1, alignItems: "center" }}>
                <Button
                    size="small"
                    variant={mode === "replace" ? "contained" : "outlined"}
                    onClick={() => setMode("replace")}
                    sx={{ fontSize: 11, textTransform: "none", py: "2px" }}
                >
                    Replace all
                </Button>
                {mergeLabel && (
                    <Button
                        size="small"
                        variant={mode === "merge" ? "contained" : "outlined"}
                        onClick={() => setMode("merge")}
                        sx={{ fontSize: 11, textTransform: "none", py: "2px" }}
                    >
                        {mergeLabel}
                    </Button>
                )}
                <Box sx={{ flex: 1 }} />
                <Button size="small" variant="contained" color="success" onClick={handleApply} sx={{ fontSize: 12, textTransform: "none" }}>
                    Apply
                </Button>
                <Button size="small" variant="outlined" onClick={onClose} sx={{ fontSize: 12, textTransform: "none" }}>
                    Cancel
                </Button>
            </Box>
        </SamplePanel>
    );
}
