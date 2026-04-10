import { Box, Typography } from "@mui/material";
import type { ReactNode } from "react";
import React from "react";

interface SectionHeaderProps {
    number?: number;
    title?: string;
    subtitle?: string;
    rightContent?: ReactNode;
    children?: ReactNode;
}

export function SectionHeader({ number, title, subtitle, rightContent, children }: SectionHeaderProps) {
    if (children && !number) {
        return (
            <Typography
                variant="caption"
                sx={(theme) => ({
                    color: theme.palette.text.secondary,
                    textTransform: "uppercase",
                    letterSpacing: "0.08em",
                    fontWeight: 600,
                })}
            >
                {children}
            </Typography>
        );
    }

    return (
        <Box display="flex" alignItems="center" gap={1.25}>
            {number != null && (
                <Box
                    sx={{
                        width: 26,
                        height: 26,
                        borderRadius: "50%",
                        backgroundColor: "#7c4dff",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        color: "#fff",
                        fontSize: "0.8rem",
                        fontWeight: 700,
                        flexShrink: 0,
                    }}
                >
                    {number}
                </Box>
            )}
            <Box display="flex" alignItems="baseline" gap={0.75} flex={1} minWidth={0}>
                {title && (
                    <Typography variant="body1" fontWeight={700} sx={{ whiteSpace: "nowrap" }}>
                        {title}
                    </Typography>
                )}
                {subtitle && (
                    <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: "nowrap" }}>
                        {"\u2014 "}
                        {subtitle}
                    </Typography>
                )}
            </Box>
            {rightContent && <Box flexShrink={0}>{rightContent}</Box>}
        </Box>
    );
}
