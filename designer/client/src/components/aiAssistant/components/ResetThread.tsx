import { useAssistantApi, useAssistantState } from "@assistant-ui/react";
import { Box, Button, Slide } from "@mui/material";
import React, { useCallback } from "react";

import { ThreadIdManager } from "../ThreadIdManager";

export const ResetThread = () => {
    const api = useAssistantApi();
    const messageCount = useAssistantState(({ thread }) => thread.messages.length);

    const threadReset = useCallback(() => {
        ThreadIdManager.reset();
        api.threads().switchToNewThread();
    }, [api]);

    return (
        <Slide in={messageCount > 0}>
            <Box
                sx={{
                    display: "flex",
                    justifyContent: "flex-end",
                    flex: 1,
                    opacity: 0.75,
                }}
            >
                <Button
                    variant="text"
                    size="small"
                    color="inherit"
                    onClick={threadReset}
                    sx={{
                        ":focus": { outline: "none" },
                        padding: 0.25,
                        margin: 0.25,
                    }}
                >
                    reset thread
                </Button>
            </Box>
        </Slide>
    );
};
