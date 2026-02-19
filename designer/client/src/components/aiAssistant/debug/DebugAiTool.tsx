import { z } from "zod";

import { useFrontendAiTool } from "../useFrontendAiTool";

export function DebugAiTool() {
    useFrontendAiTool({
        toolName: "debug",
        description: `[DEBUG TOOL] This tool is for debugging the AI assistant's frontend tools. It takes a user prompt and a response, logs them to the console, and then returns them. It may also randomly throw an error for testing purposes. This tool should not be used for any productive work.`,
        parameters: z.object({
            response: z.string().describe("The response that would be sent to the user."),
            request: z.string().describe("The user prompt that triggered this tool call."),
        }),
        execute: (args) => {
            console.log(args);
            if (Math.random() > 0.6) {
                throw new Error("random debug failure");
            }
            return { ...args, response: prepareMarkdownForCodeBlock(args.response) };
        },
    });

    return null;
}

function prepareMarkdownForCodeBlock(markdown) {
    return markdown.replaceAll("```", "\\`\\`\\`");
}
