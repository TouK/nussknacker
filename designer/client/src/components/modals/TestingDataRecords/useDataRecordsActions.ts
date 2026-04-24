import React, { useCallback, useState } from "react";

import { setTestCaseInputs } from "../../../actions/nk/testCasesActions";
import HttpService from "../../../http/HttpService/instance";
import { getProcessName, getScenarioGraph } from "../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import type { NodeType, PropertiesType } from "../../../types/node";
import type { CellError } from "../../graph/node-modal/editors/expression/Table/errorHighlights";
import { cleanProperties } from "../../graph/node-modal/requestSourceAddons";
import type { TestingDataRecords } from "./types";
import { useDataRecordsValidation } from "./useDataRecordsValidation";
import { mapGeneratedTestingDataToTableFormat } from "./utils";

export const useDataRecordsActions = (node: NodeType, scenarioProperties: PropertiesType) => {
    const [cellErrors, setCellErrors] = useState<CellError[]>([]);
    const scenarioName = useAppSelector(getProcessName);
    const scenarioGraph = useAppSelector(getScenarioGraph);
    const dispatch = useAppDispatch();
    const { recordsErrors, validateForCount } = useDataRecordsValidation();

    const handleGenerateTestData = useCallback(
        async (numberOfSamples: number) => {
            if (
                !validateForCount((currentCount) => {
                    return currentCount + (numberOfSamples || 1); // we treat 0 as 1 to run validation when limit exceeded;
                })
            )
                return;

            if (numberOfSamples > 0) {
                const { data } = await HttpService.fetchTestData(scenarioName, scenarioGraph, numberOfSamples);
                dispatch(setTestCaseInputs((prevState) => [...prevState, ...data.map(mapGeneratedTestingDataToTableFormat)]));
            }
        },
        [validateForCount, scenarioName, scenarioGraph, dispatch],
    );

    const generateTestDataForSingleSource = useCallback(
        async (numberOfSamples: number, scenarioProperties: PropertiesType, nodeData: NodeType) => {
            if (
                !validateForCount((currentCount) => {
                    return currentCount + (numberOfSamples || 1); // we treat 0 as 1 to run validation when limit exceeded;
                })
            )
                return;

            if (numberOfSamples > 0) {
                const { data } = await HttpService.fetchTestDataForSingleSource(
                    scenarioName,
                    scenarioProperties,
                    cleanProperties(nodeData),
                    numberOfSamples,
                );
                dispatch(setTestCaseInputs((prevState) => [...prevState, ...data.map(mapGeneratedTestingDataToTableFormat)]));
            }
        },
        [validateForCount, scenarioName, dispatch],
    );

    const validateEditedRow = React.useCallback(
        (rowIndex: number, row: TestingDataRecords) => {
            HttpService.validateSourceNodeTestData(scenarioName, scenarioProperties, cleanProperties(node), row).then(({ data }) =>
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
        [scenarioName, node, scenarioProperties],
    );

    const handleRowUpdated = React.useCallback(
        (rowIndex: number, row: TestingDataRecords) => {
            dispatch(
                setTestCaseInputs((prev) => {
                    const next = [...prev];
                    if (rowIndex >= next.length) {
                        for (let i = next.length; i <= rowIndex; i++) {
                            next[i] = { sourceId: "", timestamp: undefined, variables: "" } as TestingDataRecords;
                        }
                    }
                    next[rowIndex] = row;
                    return next;
                }),
            );

            validateEditedRow(rowIndex, row);
        },
        [dispatch, validateEditedRow],
    );

    const handleRowsDeleted = React.useCallback(
        async (deletedRows: number[]) => {
            if (!deletedRows.length) return;
            const deletedSet = new Set(deletedRows);
            const sorted = [...deletedRows].sort((a, b) => a - b);

            dispatch(setTestCaseInputs((prev) => prev.filter((_, i) => !deletedSet.has(i))));
            setCellErrors((prev) =>
                prev
                    .filter((e) => !deletedSet.has(e.y))
                    .map((e) => {
                        const shift = sorted.reduce((acc, r) => (r < e.y ? acc + 1 : acc), 0);
                        return shift ? { ...e, y: e.y - shift } : e;
                    }),
            );

            validateForCount((currentCount) => {
                return Math.max(0, currentCount - deletedRows.length);
            });
        },
        [dispatch, validateForCount],
    );

    const handleRowsAdded = React.useCallback(
        (rows: TestingDataRecords[]) => {
            if (!rows.length) return;
            if (
                !validateForCount((currentCount) => {
                    return currentCount + rows.length;
                })
            )
                return;

            dispatch(setTestCaseInputs((prev) => [...prev, ...rows]));
        },
        [dispatch, validateForCount],
    );

    const handleRowMoved = React.useCallback(
        (fromIndex: number, toIndex: number) => {
            dispatch(
                setTestCaseInputs((prev) => {
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
        handleRowsAdded,
        handleRowUpdated,
        handleRowsDeleted,
        handleGenerateTestData,
        generateTestDataForSingleSource,
        handleRowMoved,
    };
};
