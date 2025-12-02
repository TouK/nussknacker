import React, { useCallback, useState } from "react";

import { setTestingEventsParameters } from "../../../actions/nk/displayTestResults";
import HttpService from "../../../http/HttpService/instance";
import { getProcessName, getScenarioGraph } from "../../../reducers/selectors/graph";
import { getMaxTestingRecords } from "../../../reducers/selectors/settings";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import type { CellError } from "../../graph/node-modal/editors/expression/Table/errorHighlights";
import type { TestingDataRecords } from "./Table";
import { mapGeneratedTestingDataToTableFormat } from "./utils";

type RecordError = { type: "TEST_DATA_LIMIT_EXCEEDED" };

interface Props {
    testingDataRecords: TestingDataRecords[];
}

export const useDataRecordsActions = ({ testingDataRecords }: Props) => {
    const [cellErrors, setCellErrors] = useState<CellError[]>([]);
    const [recordsErrors, setRecordsErrors] = useState<RecordError[]>([]);
    const maxTestingRecords = useAppSelector(getMaxTestingRecords);
    const scenarioName = useAppSelector(getProcessName);
    const scenarioGraph = useAppSelector(getScenarioGraph);
    const dispatch = useAppDispatch();

    const validateForCount = React.useCallback(
        (nextCount: number) => {
            const testDataLimitExceeded = nextCount > maxTestingRecords;
            const errors: RecordError[] = [];

            if (testDataLimitExceeded) {
                errors.push({ type: "TEST_DATA_LIMIT_EXCEEDED" });
            }

            setRecordsErrors(errors);
            return errors.length === 0;
        },
        [maxTestingRecords],
    );

    const handleGenerateTestData = useCallback(
        async (numberOfSamples: number) => {
            const nextCount = testingDataRecords.length + (numberOfSamples || 1); // we treat 0 as 1 to run validation when limit exceeded
            if (!validateForCount(nextCount)) return;

            if (numberOfSamples > 0) {
                const { data } = await HttpService.generatedTestData(scenarioName, scenarioGraph, numberOfSamples);
                dispatch(setTestingEventsParameters((prevState) => [...prevState, ...data.map(mapGeneratedTestingDataToTableFormat)]));
            }
        },
        [testingDataRecords.length, validateForCount, scenarioName, scenarioGraph, dispatch],
    );

    const validateEditedRow = React.useCallback(
        (rowIndex: number, row: TestingDataRecords) => {
            HttpService.validateTestDataWithDataRecords(scenarioName, scenarioGraph, row).then(({ data }) =>
                setCellErrors((prev) => {
                    const withoutRow = prev.filter((e) => e.y !== rowIndex || e.columnName !== "variables");
                    const newErrors: CellError[] = data.validationErrors.map((validationError) => ({
                        errorMessage: validationError.message,
                        columnName: "variables",
                        x: 1, // Input variables column has static position
                        y: rowIndex,
                    }));
                    if (!newErrors.length) return withoutRow;
                    return [...withoutRow, ...newErrors];
                }),
            );
        },
        [scenarioName, scenarioGraph],
    );

    const handleRowAdded = React.useCallback(
        (rowIndex: number, row: TestingDataRecords) => {
            const nextCount = testingDataRecords.length + 1;
            if (!validateForCount(nextCount)) return;

            dispatch(
                setTestingEventsParameters((prev) => {
                    const next = [...prev];
                    if (rowIndex === next.length) next.push(row);
                    else next.splice(rowIndex, 0, row);
                    return next;
                }),
            );

            setCellErrors((prev) => prev.map((e) => (e.y >= rowIndex ? { ...e, y: e.y + 1 } : e)));
        },
        [dispatch, testingDataRecords.length, validateForCount],
    );

    const handleRowUpdated = React.useCallback(
        (rowIndex: number, row: TestingDataRecords) => {
            setTestingEventsParameters((prev) => {
                const next = [...prev];
                if (rowIndex >= next.length) {
                    for (let i = next.length; i <= rowIndex; i++) {
                        next[i] = { sourceId: "", timestamp: undefined, variables: "" } as TestingDataRecords;
                    }
                }
                next[rowIndex] = row;
                return next;
            });
            validateEditedRow(rowIndex, row);
        },
        [validateEditedRow],
    );

    const handleRowsDeleted = React.useCallback(
        async (deletedRows: number[]) => {
            if (!deletedRows.length) return;
            const deletedSet = new Set(deletedRows);
            const sorted = [...deletedRows].sort((a, b) => a - b);
            const nextCount = Math.max(0, testingDataRecords.length - deletedRows.length);

            dispatch(setTestingEventsParameters((prev) => prev.filter((_, i) => !deletedSet.has(i))));
            setCellErrors((prev) =>
                prev
                    .filter((e) => !deletedSet.has(e.y))
                    .map((e) => {
                        const shift = sorted.reduce((acc, r) => (r < e.y ? acc + 1 : acc), 0);
                        return shift ? { ...e, y: e.y - shift } : e;
                    }),
            );

            validateForCount(nextCount);
        },
        [dispatch, testingDataRecords.length, validateForCount],
    );

    const handleRowMoved = React.useCallback(
        (fromIndex: number, toIndex: number) => {
            dispatch(
                setTestingEventsParameters((prev) => {
                    if (!prev || fromIndex < 0 || toIndex < 0 || fromIndex >= prev.length || toIndex > prev.length) return prev;
                    const next = [...prev];
                    const [moved] = next.splice(fromIndex, 1);
                    next.splice(toIndex, 0, moved);
                    return next;
                }),
            );
        },
        [dispatch],
    );

    return {
        cellErrors,
        recordsErrors,
        handleRowAdded,
        handleRowUpdated,
        handleRowsDeleted,
        handleGenerateTestData,
        handleRowMoved,
    };
};
