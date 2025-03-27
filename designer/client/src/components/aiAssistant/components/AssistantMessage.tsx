import { useMessage } from "@assistant-ui/react";
import { Box, Typography } from "@mui/material";
import React from "react";

export const AssistantMessage = () => {
    const { status, content } = useMessage();

    return (
        <Box my={2}>
            {content.map((part, index) => (
                <Typography key={index}>{part.text}</Typography>
            ))}
        </Box>
    );
};
