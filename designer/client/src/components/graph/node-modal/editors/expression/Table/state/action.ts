import { TableData } from "./tableState";
import { TaggedUnion } from "type-fest";

export const ActionTypes = {
    expand: "expand",
    replaceData: "replace-data",
    editData: "edit-data",
    insertData: "insert-data",
    deleteRows: "delete-rows",
    deleteColumns: "delete-columns",
    resetColumnsSize: "reset-columns-size",
    columnResize: "column-resize",
    renameColumn: "rename-column",
    changeColumnType: "change-column-type",
} as const;

type Actions = {
    [ActionTypes.replaceData]: {
        data: TableData;
    };
    [ActionTypes.expand]: {
        rows: number;
        columns: number;
        dataType?: string;
    };
    [ActionTypes.editData]: {
        dataChanges: {
            row: number;
            column: number;
            value: string;
        }[];
    };
    [ActionTypes.insertData]: {
        column: number;
        row: number;
        input: readonly (readonly string[])[];
        dataType?: string;
        extraRowsCount?: number;
    };
    [ActionTypes.deleteRows]: {
        rows: number[];
    };
    [ActionTypes.deleteColumns]: {
        columns: number[];
    };
    [ActionTypes.resetColumnsSize]: {
        columns: number[];
    };
    [ActionTypes.columnResize]: {
        column: number;
        size: number;
    };
    [ActionTypes.renameColumn]: {
        from: string;
        to: string;
    };
    [ActionTypes.changeColumnType]: {
        column: number;
        dataType: string;
    };
};

export type Action = TaggedUnion<"type", Actions>;
