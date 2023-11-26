import { SpelExpressionEvaluator } from "spel2js";
import { DataColumn, DataRow } from "./tableState";

export function parseSpel<T>(expression: string, emptyValue?: T): T {
    try {
        return SpelExpressionEvaluator.eval(expression);
    } catch (error) {
        console.warn(error);
        return emptyValue;
    }
}

function stringify(str = "") {
    return `'${str.replaceAll(`"`, `""`).replaceAll(`'`, `''`)}'`;
}

function stringifyList(rows: string[][]) {
    let listString = "";
    for (let i = 0; i < rows.length; i++) {
        let rowString = "";
        const l = rows[i]?.length || 0;
        for (let j = 0; j < l; j++) {
            rowString += stringify(rows[i][j]);
            if (j !== l - 1) {
                rowString += ", ";
            }
        }

        listString += `  {${rowString}}`;

        if (i !== rows.length - 1) {
            listString += ",\n";
        }
    }

    return listString;
}

export function toSpel({ columns, rows }: { columns: DataColumn[]; rows: DataRow[] }): string {
    return `{\n columns:{\n${stringifyList(columns)}\n },\n rows:{\n${stringifyList(rows)}\n }\n}`;
}

export function longestRow<T>(matrix: readonly (readonly T[])[]) {
    return matrix.reduce((longestRow, row) => (longestRow.length < row.length ? row : longestRow), []);
}

export function transpose<T>(matrix: T[][], defaultValue?: T) {
    return longestRow(matrix).map((_, i) => matrix.map((row) => row[i] ?? defaultValue));
}

function getLetterColumnName(n = 0) {
    const letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    const max = letters.length;
    let result = "";
    ++n;
    while (n > 0) {
        const code = (n - 1) % max;
        result = letters[code] + result;
        n = Math.floor((n - code) / max);
    }
    return result;
}

function dedup(values: string[], name: string): string {
    if (values.includes(name)) {
        return dedup(values, `${name}_`);
    }
    return name;
}

export function getNextColumnName(names: string[], index: number) {
    return dedup(names, getLetterColumnName(index));
}
