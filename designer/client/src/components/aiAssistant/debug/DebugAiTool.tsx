import { z } from "zod";

import { useFrontendAiTool } from "../useFrontendAiTool";

export function DebugAiTool() {
    useFrontendAiTool({
        toolName: "debug",
        description: `Tool for debugging frontend tools`,
        parameters: z.object({
            response: z.string().describe("response to this user prompt"),
            request: z.string().describe("user prompt which caused this tool call"),
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
