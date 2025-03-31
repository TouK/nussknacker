import { useMessage } from "@assistant-ui/react";
import { Box, Typography } from "@mui/material";
import React from "react";
import ReactMarkdown from "react-markdown";

export const AssistantMessage = () => {
    const { status, content } = useMessage();

    return (
        <Box my={2}>
            {content.map((part, index) => (
                <ReactMarkdown key={index}>{part.text}</ReactMarkdown>
            ))}
        </Box>
    );
};
