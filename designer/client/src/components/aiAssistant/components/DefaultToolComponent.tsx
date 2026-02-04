import type { ToolCallMessagePartProps } from "@assistant-ui/react";
import { useThreadModelContext } from "@assistant-ui/react";
import { Button, ButtonGroup, Paper, Stack, Typography } from "@mui/material";
import type { PropsWithChildren } from "react";
import React from "react";

import { resolveHumanAction, useToolHumanAction } from "../PendingHumanResolvers";
import { ToolUsePermission } from "../useCheckPermission";

function HumanActionButton({ action, toolCallId, children }: PropsWithChildren<{ action: string; toolCallId: string }>) {
    if (action === ToolUsePermission.QUESTION) {
        return (
            <Stack spacing={1} component={Paper} sx={{ padding: 2, justifySelf: "flex-start" }}>
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

export function DefaultToolComponent({ status, toolCallId, toolName }: ToolCallMessagePartProps) {
    const action = useToolHumanAction(toolCallId);
    const { tools } = useThreadModelContext();
    switch (status.type) {
        case "requires-action":
        case "running":
            return action && tools[toolName] ? (
                <HumanActionButton action={action} toolCallId={toolCallId}>
                    <Typography>{tools[toolName].description}</Typography>
                </HumanActionButton>
            ) : (
                <Typography sx={(theme) => ({ color: theme.palette.action.disabled })}>waiting for result...</Typography>
            );

        default:
            return null;
    }
}
