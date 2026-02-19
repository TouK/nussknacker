import type { AssistantToolProps } from "@assistant-ui/react";
import { useAssistantTool } from "@assistant-ui/react";
import type { z } from "zod";

import { useStableCallback } from "../graph/node-modal/node/useCallbackRef";
import { useCheckPermission } from "./useCheckPermission";
import { withAbort } from "./withAbort";

type ZodRecordLike = z.ZodRecord | z.ZodObject;

export type FrontendAiToolRender<S extends ZodRecordLike, R> = AssistantToolProps<z.infer<S>, R>["render"];
type FrontendAiToolOptions<S extends ZodRecordLike, R> = {
    toolName: string;
    description: string;
    parameters: S;
    execute: (args: z.infer<S>) => R;
    render?: FrontendAiToolRender<S, R>;
};

export function useFrontendAiTool<S extends ZodRecordLike, R>({
    toolName,
    description,
    parameters,
    execute,
    render,
}: FrontendAiToolOptions<S, R>) {
    const checkPermission = useCheckPermission();

    const stableRender = useStableCallback(render);

    useAssistantTool<any, any>({
        toolName,
        type: "frontend",
        description,
        render: render && stableRender,
        parameters: parameters.toJSONSchema(),
        execute: (args, context) =>
            withAbort(context.abortSignal, async () => {
                try {
                    await checkPermission(toolName, context);
                } catch (error) {
                    return rejectToolCall(error);
                }
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
