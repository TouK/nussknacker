import React from "react";
import { useMessage } from "@assistant-ui/react";
import { Box, Typography } from "@mui/material";

export const UserMessage = () => {
    const { content } = useMessage();

    return (
        <Box sx={{ display: "flex", flexDirection: "column" }}>
            {content.map(({ text, id }) => {
                return <Typography key={id}>Question: {text}</Typography>;
            })}
        </Box>
    );
};
