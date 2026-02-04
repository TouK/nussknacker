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
            return { args };
        },
    });

    return null;
}
