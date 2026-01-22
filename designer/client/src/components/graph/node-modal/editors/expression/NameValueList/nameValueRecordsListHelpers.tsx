import { prettyPrint } from "ts-spel";

import { getAst, printFragment } from "../../../aggregate/pareserHelpers";

export type NameValueRecord = Record<"name" | "value", string>;

export function serialize(input: NameValueRecord[]): string {
    if (!input) return "";

    const records = input
        .filter(Boolean)
        .map(({ name, value }) => {
            const content = Object.entries({ name, value })
                .map(([key, value]) => {
                    if (value) {
                        const ast = getAst(value, true);
                        if (ast) return `${key}:${prettyPrint(ast)}`;
                        return `${key}:${value}`;
                    }
                    if (key === "name") return `${key}:""`;
                    return "";
                })
                .filter(Boolean)
                .join(", ");
            return `{${content}}`;
        })
        .filter(Boolean)
        .join(",\n");

    return ["{", records, "}"].filter(Boolean).join("\n");
}

export function deserialize(input: string, graceful = false): NameValueRecord[] {
    const listAst = getAst(input, graceful);
    if (listAst?.type !== "InlineList" || listAst.__unclosed) return null;

    return listAst.elements
        .map((el) => printFragment(input, el))
        .map((input) => {
            const ast = getAst(input, graceful);
            if (ast?.type !== "InlineMap") return null;
            return Object.fromEntries(Object.entries(ast.elements).map(([key, el]) => [key, printFragment(input, el)])) as NameValueRecord;
        })
        .filter(Boolean);
}
