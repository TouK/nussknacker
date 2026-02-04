import { useAssistantTool } from "@assistant-ui/react";
import type { z } from "zod";

import { useCheckPermission } from "./useCheckPermission";
import { withAbort } from "./withAbort";

type FrontendAiToolOptions<S extends z.ZodObject, R> = {
    toolName: string;
    description: string;
    parameters: S;
    execute: (args: z.infer<S>) => R;
};

export function useFrontendAiTool<S extends z.ZodObject, R>({ toolName, description, parameters, execute }: FrontendAiToolOptions<S, R>) {
    const checkPermission = useCheckPermission();
    useAssistantTool<any, any>({
        toolName,
        type: "frontend",
        description,
        parameters: parameters.toJSONSchema(),
        execute: (args, context) =>
            withAbort(context.abortSignal, async () => {
                await checkPermission(toolName, context);
                return execute(args);
            }),
    });
}
