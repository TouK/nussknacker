import { prettyPrint } from "ts-spel";

import { getAst, parseToList, parseToObject } from "../../../aggregate/pareserHelpers";

export type NameValueRecord = Record<"name" | "value", string>;
export type NameValueRecordList = NameValueRecord[];

export function serialize(input: NameValueRecordList): string {
    if (!input) return `{}`;

    const records = input.filter(Boolean).map(({ name, value }) => {
        const content = Object.entries({ name, value })
            .map(([key, value]) => {
                if (value !== undefined) {
                    const ast = getAst(value);
                    if (ast) {
                        return `${key}:${prettyPrint(ast)}`;
                    }
                } else {
                    if (key === "value") return "";
                    return `${key}:""`;
                }
            })
            .filter(Boolean)
            .join(", ");
        return ` {${content}}`;
    });

    return `{\n${records.join(",\n")}\n}`;
}

export function deserialize(input: string): NameValueRecordList {
    return parseToList(input)?.map(parseToObject<NameValueRecord>);
}
