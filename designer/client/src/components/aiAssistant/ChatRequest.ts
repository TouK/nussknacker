import type { TextMessagePart, Tool } from "@assistant-ui/react";
import type { Tagged } from "type-fest";

export type ToolName = Tagged<string, "ToolName">;

interface ChatExternalTool {
    name: ToolName;
    description: string;
    parameters: Tool["parameters"];
}

export type ToolCallId = Tagged<string, "ToolCallId">;
export type ToolResult = unknown;

export type ToolExecutionResult = {
    id: ToolCallId;
    name: ToolName;
    result: ToolResult;
};

export enum ChatRequestType {
    MESSAGE = "message",
    TOOL_RESULTS = "toolResults",
}

interface BaseChatRequest {
    threadId: string;
    externalTools: ChatExternalTool[];
}

export interface ChatRequestWithMessage extends BaseChatRequest {
    type: ChatRequestType.MESSAGE;
    message: Pick<TextMessagePart, "text">;
}

export interface ChatRequestWithToolResults extends BaseChatRequest {
    type: ChatRequestType.TOOL_RESULTS;
    toolExecutionResults: ToolExecutionResult[];
}

export type ChatRequest = ChatRequestWithMessage | ChatRequestWithToolResults;
