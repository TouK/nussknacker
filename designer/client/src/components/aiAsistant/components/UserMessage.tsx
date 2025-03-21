import React from "react";
import { useMessage } from "@assistant-ui/react";
import { Box, Typography } from "@mui/material";

export const UserMessage = () => {
    const { content, ...rest } = useMessage();
    const questionLabel = "Question:";

    console.log(rest);
    return (
        <Box sx={{ display: "flex", flexDirection: "column" }}>
            {content.map((part, index) => {
                return (
                    <Typography key={index}>
                        {questionLabel} {part.text}
                    </Typography>
                );
            })}
        </Box>
    );
};
