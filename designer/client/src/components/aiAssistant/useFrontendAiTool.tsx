import { useAssistantTool } from "@assistant-ui/react";
import type { z } from "zod";

import { useCheckPermission } from "./useCheckPermission";
import { withAbort } from "./withAbort";

type ZodRecordLike = z.ZodRecord | z.ZodObject;

type FrontendAiToolOptions<S extends ZodRecordLike, R> = {
    toolName: string;
    description: string;
    parameters: S;
    execute: (args: z.infer<S>) => R;
};

export function useFrontendAiTool<S extends ZodRecordLike, R>({ toolName, description, parameters, execute }: FrontendAiToolOptions<S, R>) {
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
        experimental_onSchemaValidationError: () => rejectToolCall("parameters are not schema-compliant"),
    });
}

export function rejectToolCall(reason: string) {
    return {
        status: "rejected",
        reason,
    };
}
