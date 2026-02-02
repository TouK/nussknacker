import { delay } from "../../../utils";
import type { ChatRequest } from "../ChatRequest";
import type { ChatStreamEventName } from "../InitializeChatStream";

function toSSE({ data, event = "delta" }: { data: string; event?: ChatStreamEventName }) {
    return `\ndata: ${data}\nevent: ${event}\n\n`;
}

function withEcho(message: string) {
    return `\\<echo\\> \`\`\`${message}\`\`\``;
}

async function* streamMock(message = "abc", threadId = crypto.randomUUID()) {
    for (const ch of withEcho(message).split("").filter(Boolean)) {
        await delay(Math.round(Math.random() * 60));
        yield { data: JSON.stringify({ text: ch }) };
    }
    if (message.includes(".")) {
        await delay(500);
        yield {
            event: "toolExecutionRequest",
            data: `{ "type": "tool", "name": "debug", "arguments": ${JSON.stringify({ request: message, response: withEcho(message) })} }`,
        };
    }
    await delay(500);
    yield {
        event: "stop",
        data: `{"threadId":"${threadId}"}`,
    };
}

export function mockAssitantFetch({ threadId, message, externalTools }: ChatRequest, signal: AbortSignal) {
    console.debug({ message, threadId, externalTools });
    const encoder = new TextEncoder();

    const stream = new ReadableStream({
        async start(controller) {
            const abort = () => controller.error(new DOMException("Aborted", "AbortError"));

            signal?.addEventListener("abort", abort);

            try {
                for await (const msg of streamMock(message.text, threadId)) {
                    if (signal?.aborted) throw new DOMException("Aborted", "AbortError");
                    controller.enqueue(encoder.encode(toSSE(msg)));
                }
                controller.close();
            } finally {
                signal?.removeEventListener("abort", abort);
            }
        },
    });

    return Promise.resolve(new Response(stream));
}
