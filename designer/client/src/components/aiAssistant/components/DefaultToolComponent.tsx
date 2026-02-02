import type { ToolCallMessagePartProps } from "@assistant-ui/react";
import { Typography } from "@mui/material";
import type { PropsWithChildren } from "react";
import React from "react";

import { resolveHumanAction, useToolHumanAction } from "../PendingHumanResolvers";

function HumanActionButton({ action, toolCallId }: PropsWithChildren<{ action: string; toolCallId: string }>) {
    if (action === "execute allowed?") {
        return (
            <>
                <h1>{action}</h1>
                {["yes", "always", "no"].map((response) => (
                    <button key={response} onClick={() => resolveHumanAction(toolCallId, response)}>
                        {response}
                    </button>
                ))}
            </>
        );
    }
    return (
        <button
            onClick={() => {
                resolveHumanAction(toolCallId);
            }}
        >
            {action}
        </button>
    );
}

export function DefaultToolComponent({ status, toolCallId }: ToolCallMessagePartProps) {
    const action = useToolHumanAction(toolCallId);
    switch (status.type) {
        case "requires-action":
        case "running":
            return action ? (
                <HumanActionButton action={action} toolCallId={toolCallId}></HumanActionButton>
            ) : (
                <Typography sx={(theme) => ({ color: theme.palette.action.disabled })}>waiting for result...</Typography>
            );

        default:
            return null;
    }
}
