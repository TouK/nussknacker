import { TableData } from "./tableState";

type MatrixElement<M> = M extends Matrix<infer T> ? T : never;
type Matrix<I> = readonly (readonly I[])[];

export function longestRow<M, I = MatrixElement<M>>(matrix: Matrix<I>): readonly I[] {
    return matrix.reduce((longestRow, row) => (longestRow.length < row.length ? row : longestRow), [] as I[]);
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

export function getNextColumnName(names: string[], index: number, name?: string) {
    return dedup(names, name || getLetterColumnName(index));
}

function expandArray<T>(array: T[], length: number, fill?: (i: number) => T): T[] {
    if (length <= 0) {
        return array;
    }
    const newValues = Array.from({ length }, (_, i: number) => fill?.(array.length + i));
    return [...array, ...newValues];
}

export function expandTable(state: TableData, rowsNum = 0, colsNum = 0, dataType?: string): TableData {
    if (rowsNum <= 0 && colsNum <= 0) {
        return state;
    }

    const currentNames = state.columns.map(({ name }) => name);
    const columns = expandArray(state.columns, colsNum, (i) => {
        return {
            name: getNextColumnName(currentNames, i),
            type: dataType,
        };
    });

    const rows = expandArray(state.rows, rowsNum).map((r = []) => {
        return expandArray(r, columns.length - r.length, () => null);
    });

    return {
        columns,
        rows,
    };
}

export function normalizeValue(value: string) {
    return value.trim() || null;
}
