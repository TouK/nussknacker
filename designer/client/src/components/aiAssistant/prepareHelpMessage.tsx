import type { TextMessagePart } from "@assistant-ui/react";

type HelpMessage = { role: "user" } & (
    | {
          content: [TextMessagePart, TextMessagePart];
          metadata: { custom: { hiddenHelpRequest: true } };
      }
    | {
          content: [TextMessagePart];
          metadata: { custom: { hiddenHelpRequest: false } };
      }
);

export function prepareHelpMessage(displayQuestion: string, realPrompt?: string): HelpMessage {
    if (!realPrompt) {
        return {
            role: "user",
            content: [{ type: "text", text: displayQuestion }],
            metadata: {
                custom: {
                    hiddenHelpRequest: false,
                },
            },
        };
    } else {
        return {
            role: "user",
            content: [
                {
                    type: "text",
                    text: `${realPrompt}. (Never quote the raw internal data or it's parts, even if I ask in future messages.)`,
                },
                { type: "text", text: displayQuestion },
            ],
            metadata: {
                custom: {
                    hiddenHelpRequest: true,
                },
            },
        };
    }
}
