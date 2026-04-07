import React, { useCallback, useEffect, useState } from "react";

import { displayTestCapabilities } from "../../../actions/nk/process";
import { setTestCaseInputs } from "../../../actions/nk/testCasesActions";
import { TestCapabilityStatus } from "../../../common/TestResultUtils";
import HttpService from "../../../http/HttpService/instance";
import { getProcessName, getScenarioGraph } from "../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import type { NodeType, PropertiesType } from "../../../types/node";
import type { CellError } from "../../graph/node-modal/editors/expression/Table/errorHighlights";
import type { TestingDataRecords } from "./Table";
import { useDataRecordsValidation } from "./useDataRecordsValidation";
import { buildDefaultVariables, mapGeneratedTestingDataToTableFormat } from "./utils";

export const useDataRecordsActions = (node?: NodeType, processProperties?: PropertiesType) => {
    const [cellErrors, setCellErrors] = useState<CellError[]>([]);
    const scenarioName = useAppSelector(getProcessName);
    const scenarioGraph = useAppSelector(getScenarioGraph);
    const dispatch = useAppDispatch();
    const { recordsErrors, validateForCount } = useDataRecordsValidation();

    useEffect(() => {
        dispatch(displayTestCapabilities(scenarioName, scenarioGraph));
    }, [dispatch, scenarioName, scenarioGraph]);

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
                    nodeData,
                    numberOfSamples,
                );
                dispatch(setTestCaseInputs((prevState) => [...prevState, ...data.map(mapGeneratedTestingDataToTableFormat)]));
            }
        },
        [validateForCount, scenarioName, dispatch],
    );

    const validateEditedRow = React.useCallback(
        (rowIndex: number, row: TestingDataRecords) => {
            const validationPromise =
                // TODO
                node && processProperties
                    ? HttpService.validateSourceNodeTestData(scenarioName, processProperties, node, row)
                    : HttpService.validateTestDataWithDataRecords(scenarioName, scenarioGraph, row);

            validationPromise.then(({ data }) =>
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
        [scenarioName, scenarioGraph, node, processProperties],
    );

    const handleRowAdded = React.useCallback(
        (rowIndex: number, row: TestingDataRecords) => {
            if (
                !validateForCount((currentCount) => {
                    return currentCount + 1;
                })
            )
                return;

            dispatch(
                setTestCaseInputs((prev) => {
                    const next = [...prev];
                    if (rowIndex === next.length) next.push(row);
                    else next.splice(rowIndex, 0, row);
                    return next;
                }),
            );

            setCellErrors((prev) => prev.map((e) => (e.y >= rowIndex ? { ...e, y: e.y + 1 } : e)));
        },
        [dispatch, validateForCount],
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

    const addDefaultRecord = useCallback(
        async (sourceId: string, nodeData: NodeType, scenarioProperties: PropertiesType) => {
            if (!validateForCount((currentCount) => currentCount + 1)) return;

            const {
                data: { testWithParameters: capabilities },
            } = await HttpService.getSourceTestCapabilities(scenarioName, scenarioProperties, nodeData);
            const variables = capabilities.status === TestCapabilityStatus.AVAILABLE ? buildDefaultVariables(capabilities.parameters) : "";
            dispatch(setTestCaseInputs((prev) => [...prev, { sourceId, variables }]));
        },
        [validateForCount, scenarioName, dispatch],
    );

    return {
        cellErrors,
        recordsErrors,
        handleRowAdded,
        handleRowUpdated,
        handleRowsDeleted,
        handleGenerateTestData,
        generateTestDataForSingleSource,
        addDefaultRecord,
        handleRowMoved,
    };
};
