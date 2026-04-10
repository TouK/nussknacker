import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { Accordion, AccordionDetails, AccordionSummary, Box, IconButton, Tooltip, Typography } from "@mui/material";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

interface Props {
    sql: string;
}

export function SqlPreview({ sql }: Props) {
    const { t } = useTranslation();
    const [copied, setCopied] = useState(false);

    const handleCopy = () => {
        navigator.clipboard.writeText(sql).then(() => {
            setCopied(true);
            setTimeout(() => setCopied(false), 1500);
        });
    };

    return (
        <Accordion
            disableGutters
            elevation={0}
            sx={(theme) => ({
                backgroundColor: theme.palette.background.default,
                border: `1px solid ${theme.palette.divider}`,
                "&:before": { display: "none" },
                borderRadius: "6px !important",
                overflow: "hidden",
            })}
        >
            <AccordionSummary
                expandIcon={<ExpandMoreIcon sx={{ fontSize: "1.1rem" }} />}
                sx={(theme) => ({
                    minHeight: 36,
                    py: 0,
                    px: 1.5,
                    "&.Mui-expanded": { minHeight: 36 },
                    "& .MuiAccordionSummary-content": { my: 0.75 },
                })}
            >
                <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ textTransform: "uppercase", letterSpacing: "0.08em", fontWeight: 600, fontSize: "0.7rem" }}
                >
                    {t("flinkSql.preview.title", "Generated SQL")}
                </Typography>
            </AccordionSummary>
            <AccordionDetails sx={{ p: 0, position: "relative" }}>
                <Tooltip title={copied ? t("flinkSql.preview.copied", "Copied!") : t("flinkSql.preview.copy", "Copy")}>
                    <IconButton size="small" onClick={handleCopy} sx={{ position: "absolute", top: 4, right: 4, zIndex: 1 }}>
                        <ContentCopyIcon sx={{ fontSize: "0.9rem" }} />
                    </IconButton>
                </Tooltip>
                <Box
                    component="pre"
                    sx={(theme) => ({
                        m: 0,
                        p: 1.5,
                        pr: 5,
                        fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
                        fontSize: "0.75rem",
                        color: theme.palette.text.primary,
                        overflowX: "auto",
                        whiteSpace: "pre",
                        lineHeight: 1.6,
                        backgroundColor: "rgba(0,0,0,0.15)",
                    })}
                >
                    {sql}
                </Box>
            </AccordionDetails>
        </Accordion>
    );
}
