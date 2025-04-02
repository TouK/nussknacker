import { useMessage } from "@assistant-ui/react";
import { Box, CircularProgress, Typography } from "@mui/material";
import React from "react";
import ReactMarkdown from "react-markdown";

export const AssistantMessage = () => {
    const { status, content } = useMessage();

    return (
        <Box>
            {status.type === "running" ? (
                <Box display="flex" alignItems="center" gap={1}>
                    <CircularProgress size="1rem" />
                    <Typography>Running...</Typography>
                </Box>
            ) : (
                content.map((part, index) => <ReactMarkdown key={index}>{part.text}</ReactMarkdown>)
            )}
        </Box>
    );
};
