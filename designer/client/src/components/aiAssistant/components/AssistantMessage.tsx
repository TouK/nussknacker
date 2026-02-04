import type { TextMessagePartProps } from "@assistant-ui/react";
import { Box, Skeleton, Stack, Typography } from "@mui/material";
import React from "react";

import { MarkdownStyled } from "../../graph/node-modal/MarkdownStyled";
import { ActionsContainer, useHandleActions } from "./actions/ActionsContainer";
import { CopyContent } from "./actions/CopyContent";
import { RefreshAssistantAnswer } from "./actions/RefreshAssistantAnswer";

function SkeletonSentence() {
    const randomInt = (min: number, max: number) => Math.floor(Math.random() * (max - min)) + min;
    const getWidth = () => randomInt(20, 120);
    const getWords = () =>
        Array(randomInt(2, 12))
            .fill(null)
            .map((item, i) => Math.random().toString(36).substring(5));
    return (
        <Stack
            component={Typography}
            variant="body2"
            direction="row"
            sx={{
                marginBlockStart: "1em",
                marginBlockEnd: "1em",
                flexWrap: "wrap",
                height: "3em",
                overflow: "hidden",
                alignItems: "flex-start",
                alignContent: "flex-start",
            }}
        >
            {getWords().map((item) => (
                <Skeleton key={item} variant="text" width={getWidth()} sx={{ marginRight: 1 }} />
            ))}
        </Stack>
    );
}

export const AssistantMessage = ({ status, text }: TextMessagePartProps) => {
    const { showActions, handleShowActions, handleHideActions } = useHandleActions();

    if (status.type === "running" && text.length === 0) {
        return (
            <Box position={"relative"} onMouseEnter={handleShowActions} onMouseLeave={handleHideActions}>
                <Box position={"relative"} pb={0.5}>
                    <SkeletonSentence />
                </Box>
            </Box>
        );
    }

    if ((status.type === "complete" && text.length === 0) || (status.type === "incomplete" && status.reason === "cancelled")) {
        return null;
    }

    if (status.type === "running" || status.type === "complete") {
        return (
            <Box position={"relative"} onMouseEnter={handleShowActions} onMouseLeave={handleHideActions}>
                <Box position={"relative"} pb={0.5}>
                    <MarkdownStyled>{text}</MarkdownStyled>
                    {status.type === "running" ? <SkeletonSentence /> : null}

                    <ActionsContainer show={status.type === "complete" && showActions} placement={"left"}>
                        <CopyContent text={text} />
                        <RefreshAssistantAnswer />
                    </ActionsContainer>
                </Box>
            </Box>
        );
    }

    if (status.type === "incomplete" && status.reason === "error") {
        return (
            <Box position={"relative"} onMouseEnter={handleShowActions} onMouseLeave={handleHideActions}>
                <Box position={"relative"} pb={0.5}>
                    <MarkdownStyled sx={{ color: "error.main" }}>{status.error?.toString()}</MarkdownStyled>
                    <ActionsContainer show={showActions} placement={"left"}>
                        <RefreshAssistantAnswer />
                    </ActionsContainer>
                </Box>
            </Box>
        );
    }
};
