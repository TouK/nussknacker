import type { TextMessagePart, Tool } from "@assistant-ui/react";

interface ChatExternalTool {
    name: string;
    description: string;
    parameters: Tool["parameters"];
}

export interface ChatRequest {
    message: Pick<TextMessagePart, "text">;
    threadId: string;
    externalTools?: ChatExternalTool[];
}
