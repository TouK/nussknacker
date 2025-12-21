export const AI_ASSISTANT_MODAL_ID = "AI_ASSISTANT";

export function isAiAssistantDialog(windowId: string) {
    return windowId === AI_ASSISTANT_MODAL_ID;
}
