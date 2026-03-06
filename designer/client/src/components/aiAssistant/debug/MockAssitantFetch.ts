import { delay } from "../../../utils";
import type { ChatRequest } from "../ChatRequest";
import type { ChatStreamEventName } from "../InitializeChatStream";

function toSSE({ data, event = "delta" }: { data: string; event?: ChatStreamEventName }) {
    return `\ndata: ${data}\nevent: ${event}\n\n`;
}

function withEcho(message: string) {
    let string = `echo: \`\`\`${message}\`\`\``;
    if (message.split("\n").length > 1) {
        string = `echo: \n\n\`\`\`json\n${message}\n\`\`\``;
    }
    if (message.startsWith("/table")) {
        const table = [
            ["a1", "b1", "c1"],
            ["a2", "b2", "c2"],
            ["a3", "b3", "c3"],
        ];
        string += `\n\n${tableToMarkdown(table)}`;
    }
    return string;
}

function tableToMarkdown(table: string[][]): string {
    if (!Array.isArray(table) || table.length === 0) return "";

    const escape = (s: string): string => String(s).replace(/\|/g, "\\|");

    const header = "| " + table[0].map(escape).join(" | ") + " |";
    const separator = "| " + table[0].map(() => "---").join(" | ") + " |";
    const rows = table.slice(1).map((r) => "| " + r.map(escape).join(" | ") + " |");

    return [header, separator, ...rows].join("\n");
}

async function* streamMock(request = "abc", threadId: string = crypto.randomUUID()) {
    const response = withEcho(request);

    for (const ch of response.split("").filter(Boolean)) {
        await delay(Math.round(Math.random() * 60));
        yield {
            data: JSON.stringify({ text: ch }),
        };
    }

    if (request.startsWith("/")) {
        await delay(500);
        const match = request.match(/^\/(.*)(\s|$)/);
        const toolName = match?.[1] || `debug`;
        yield {
            event: "toolExecutionRequest",
            data: `{ "type": "tool", "name": "${toolName}", "arguments": ${JSON.stringify({ request, response })} }`,
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
