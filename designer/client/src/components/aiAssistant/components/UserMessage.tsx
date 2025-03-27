import { useMessage } from "@assistant-ui/react";
import { Box, Typography } from "@mui/material";
import React from "react";

export const UserMessage = () => {
    const { content, ...rest } = useMessage();

    console.log(rest);
    return (
        <Box sx={{ display: "flex", justifyContent: "flex-end", width: "100%" }}>
            <Box
                sx={{
                    display: "flex",
                    flexDirection: "column",
                    backgroundColor: "grey.700",
                    borderRadius: 2,
                    padding: 1,
                    maxWidth: "70%",
                }}
            >
                {content.map((part, index) => (
                    <Typography key={index}>{part.text}</Typography>
                ))}
            </Box>
        </Box>
    );
};
