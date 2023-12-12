import { TableData } from "./tableState";
import { ExpressionLang } from "../types";

const parseWithDefault =
    (parse: (text: string) => TableData) =>
    (expression: string, emptyValue?: TableData): TableData => {
        try {
            const { rows, columns } = parse(expression);
            return { rows, columns };
        } catch (error) {
            console.warn(error);
            return emptyValue;
        }
    };

export const parsers: Record<string, (expression: string, emptyValue?: TableData) => TableData> = {
    [ExpressionLang.JSON]: parseWithDefault(JSON.parse),
};

export const stringifiers: Record<string, (data: TableData) => string> = {
    [ExpressionLang.JSON]: (data) => JSON.stringify(data, null, 2),
};

type MatrixElement<M> = M extends Matrix<infer T> ? T : never;
type Matrix<I> = readonly (readonly I[])[];

export function longestRow<M, I = MatrixElement<M>>(matrix: Matrix<I>): readonly I[] {
    return matrix.reduce((longestRow, row) => (longestRow.length < row.length ? row : longestRow), [] as I[]);
}

export function transpose<M, I = MatrixElement<M>>(matrix: Matrix<I>, defaultValue?: I): Matrix<I> {
    return Array.from(longestRow(matrix), (_, i) => matrix.map((row) => row[i] ?? defaultValue));
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
