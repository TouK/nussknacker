// NameValue records list serialization/deserialization.
// Handles inline list of inline maps: [{name: "x", value: "y"}, ...]

import { astToSpelString, inlineMapToRecord, isInlineList, parseSpelToAst } from "../../../../../../common/spelHelpers";
import { printFragment } from "../../../aggregate/pareserHelpers";

export type NameValueRecord = Record<"name" | "value", string>;

export function serialize(input: NameValueRecord[]): string {
    if (!input) return "";

    const records = input
        .filter(Boolean)
        .map(({ name, value }) => {
            const content = Object.entries({ name, value })
                .map(([key, value]) => {
                    if (value) {
                        const ast = parseSpelToAst(value, true);
                        if (ast) return `${key}:${astToSpelString(ast)}`;
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
    const listAst = parseSpelToAst(input, graceful);
    if (!isInlineList(listAst)) return null;

    return listAst.elements
        .map((el) => printFragment(input, el))
        .map((fragmentInput) => {
            const ast = parseSpelToAst(fragmentInput, graceful);
            return inlineMapToRecord(fragmentInput, ast) as NameValueRecord;
        })
        .filter(Boolean);
}
