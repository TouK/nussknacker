import type { ToolCallMessagePartProps, ToolCallMessagePartStatus } from "@assistant-ui/react";
import { useThreadModelContext } from "@assistant-ui/react";
import { Button, ButtonGroup, Paper, Stack, styled, Typography } from "@mui/material";
import type { PropsWithChildren } from "react";
import React from "react";

import { resolveHumanAction, useToolHumanAction } from "../PendingHumanResolvers";
import { ToolUsePermission } from "../useCheckPermission";

function HumanActionButton({ action, toolCallId, children }: PropsWithChildren<{ action: string; toolCallId: string }>) {
    if (action === ToolUsePermission.QUESTION) {
        return (
            <Stack spacing={1} component={Paper} sx={{ padding: 2, marginY: 0.5, justifySelf: "flex-start" }}>
                {children}
                <ButtonGroup variant="outlined" size="small">
                    {Object.keys(ToolUsePermission.ANSWER).map((key) => (
                        <Button key={key} onClick={() => resolveHumanAction(toolCallId, ToolUsePermission.ANSWER[key])}>
                            {ToolUsePermission.ANSWER[key]}
                        </Button>
                    ))}
                </ButtonGroup>
            </Stack>
        );
    }
    return (
        <Button
            onClick={() => {
                resolveHumanAction(toolCallId);
            }}
        >
            {children}
        </Button>
    );
}

const Error = styled(Typography)(({ theme }) => ({ color: theme.palette.error.main }));
const Placeholder = styled(Typography)(({ theme }) => ({ color: theme.palette.action.disabled }));

function parseError(status: ToolCallMessagePartStatus) {
    if (status.type === "incomplete" && typeof status.error === "string") {
        return `Tool execution failed: ${status.error.replace(/^\[string\] /, "")}`;
    }
    return "Tool execution failed!";
}

export function DefaultToolComponent({ status, toolCallId, toolName, children }: PropsWithChildren<ToolCallMessagePartProps>) {
    const action = useToolHumanAction(toolCallId);
    const { tools } = useThreadModelContext();

    if (status.type === "complete") return null;

    if ((status.type === "incomplete" && status.reason === "error") || !tools[toolName]) {
        return <Error>{parseError(status)}</Error>;
    }

    if (status.type === "requires-action" || status.type === "running") {
        if (!action) return null;
        return (
            <HumanActionButton action={action} toolCallId={toolCallId}>
                {children || <Typography>{tools[toolName].description}</Typography>}
            </HumanActionButton>
        );
    }

    return <Placeholder>waiting for results...</Placeholder>;
}
