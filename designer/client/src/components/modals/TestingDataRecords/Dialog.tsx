import { Box, Typography } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import type { ElementType, ReactElement } from "react";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { testScenarioWithDataRecords } from "../../../actions/nk/displayTestResults";
import HttpService from "../../../http/HttpService/instance";
import { getProcessName, getScenarioGraph, getTestCapabilities, getTestingDataRecords } from "../../../reducers/selectors/graph";
import { getMaxTestingRecords } from "../../../reducers/selectors/settings";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import { LoadingButtonTypes } from "../../../windowManager/LoadingButton";
import { WindowContent } from "../../../windowManager/WindowContent";
import type { WindowKind } from "../../../windowManager/WindowKind";
import type { CellError } from "../../graph/node-modal/editors/expression/Table/errorHighlights";
import { InfoTooltip } from "../../graph/node-modal/editors/InfoTooltip/InfoTooltip";
import { ContentSize } from "../../graph/node-modal/node/ContentSize";
import { WindowHeaderIconStyled } from "../../graph/node-modal/nodeDetails/NodeDetailsStyled";
import { AppendFromLiveDataButton } from "./AppendFromLiveDataButton";
import { LimitExceededWarning } from "./LimitExceededWarning";
import type { TestingDataRecords } from "./Table";
import { Table } from "./Table";
import { mapGeneratedTestingDataToTableFormat } from "./utils";

type DocsLink = {
    url: string;
    label?: string;
};

export type TestingViewParams = {
    Icon?: ElementType;
    docs?: DocsLink;
    // may contain a ::form-fields or ::form-field{name=""} directives
    markdownContent?: string;
};

export interface TestingData {
    viewParams: TestingViewParams;
}
type RecordError = { type: "TEST_DATA_LIMIT_EXCEEDED" };

function Dialog(props: WindowContentProps<WindowKind, TestingData>): ReactElement {
    const maxTestingRecords = useAppSelector(getMaxTestingRecords);

    const { t } = useTranslation();
    const { data, close } = props;
    const {
        meta: { viewParams },
        kind,
    } = data;

    const dispatch = useAppDispatch();
    const testCapabilities = useAppSelector(getTestCapabilities);
    const testingDataRecords = useAppSelector(getTestingDataRecords);
    const scenarioName = useAppSelector(getProcessName);
    const scenarioGraph = useAppSelector(getScenarioGraph);

    const defaultParameter = testCapabilities.testWithParameters.sourceParameters[0];
    const [recordsErrors, setRecordsErrors] = useState<RecordError[]>([]);

    const defaultDataRecord = useMemo(
        () =>
            defaultParameter
                ? {
                      sourceId: defaultParameter.sourceId,
                      timestamp: undefined,
                      variables: defaultParameter.parameters?.[0]?.defaultValue?.expression ?? "",
                  }
                : { sourceId: undefined, timestamp: undefined, variables: undefined },
        [defaultParameter],
    );

    const [dataRecords, setDataRecords] = useState<TestingDataRecords[]>(testingDataRecords || []);
    const [cellErrors, setCellErrors] = useState<CellError[]>([]);

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
            const nextCount = dataRecords.length + (numberOfSamples || 1); // we treat 0 as 1 to run validation when limit exceeded
            if (!validateForCount(nextCount)) return;

            const { data } = await HttpService.generatedTestData(scenarioName, scenarioGraph, numberOfSamples);
            setDataRecords((prevState) => [...prevState, ...data.map(mapGeneratedTestingDataToTableFormat)]);
        },
        [dataRecords.length, validateForCount, scenarioName, scenarioGraph],
    );

    const sourceOptions = useMemo(
        () => testCapabilities.testWithParameters.sourceParameters.flatMap((sourceParameter) => sourceParameter.sourceId),
        [testCapabilities.testWithParameters.sourceParameters],
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

    const handleRowUpdated = React.useCallback(
        (rowIndex: number, row: TestingDataRecords) => {
            setDataRecords((prev) => {
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

    const handleRowAdded = React.useCallback(
        (rowIndex: number, row: TestingDataRecords) => {
            const nextCount = dataRecords.length + 1;
            if (!validateForCount(nextCount)) return;

            setDataRecords((prev) => {
                const next = [...prev];
                if (rowIndex === next.length) next.push(row);
                else next.splice(rowIndex, 0, row);
                return next;
            });
            setCellErrors((prev) => prev.map((e) => (e.y >= rowIndex ? { ...e, y: e.y + 1 } : e)));
        },
        [dataRecords.length, validateForCount],
    );

    const handleRowsDeleted = React.useCallback(
        async (deletedRows: number[]) => {
            if (!deletedRows.length) return;
            const deletedSet = new Set(deletedRows);
            const sorted = [...deletedRows].sort((a, b) => a - b);
            const nextCount = Math.max(0, dataRecords.length - deletedRows.length);

            setDataRecords((prev) => prev.filter((_, i) => !deletedSet.has(i)));
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
        [dataRecords.length, validateForCount],
    );

    const handleRowMoved = React.useCallback((fromIndex: number, toIndex: number) => {
        setDataRecords((prev) => {
            if (!prev || fromIndex < 0 || toIndex < 0 || fromIndex >= prev.length || toIndex > prev.length) return prev;
            const next = [...prev];
            const [moved] = next.splice(fromIndex, 1);
            next.splice(toIndex, 0, moved);
            return next;
        });
    }, []);

    const recordsToAddLimitExceeded = useMemo(
        () => recordsErrors.some((recordsErrors) => recordsErrors.type === "TEST_DATA_LIMIT_EXCEEDED"),
        [recordsErrors],
    );

    const disableTestButton = dataRecords.length === 0 || cellErrors.length > 0;
    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("testingForm.cancelButton.label", "Cancel"), action: () => close(), classname: LoadingButtonTypes.secondaryButton },
            {
                disabled: disableTestButton,
                title: t("testingForm.testButton.label", "Test"),
                action: () => {
                    try {
                        dispatch(testScenarioWithDataRecords(dataRecords));
                        close();
                    } catch (e) {
                        console.error(e.message);
                    }
                },
            },
        ],
        [close, disableTestButton, dispatch, dataRecords, t],
    );

    return (
        <WindowContent
            {...props}
            icon={<WindowHeaderIconStyled as={viewParams.Icon} type={kind} />}
            subheader={
                <Box display={"flex"} alignItems={"center"}>
                    <InfoTooltip
                        variant={"hover"}
                        title={t(
                            "testingDialog.description",
                            `Use prepared set of input data records to verify the scenario before deploying it. <br /> The sources will be stubbed with data records below during test invocation.`,
                        )}
                    />
                </Box>
            }
            buttons={buttons}
        >
            <ContentSize>
                <Box sx={(theme) => ({ height: "100%", display: "flex", flexDirection: "column", padding: theme.spacing(0, 2, 2) })}>
                    <Typography mt={0} variant={"h3"}>
                        {t("testingDialog.label.inputDataRecords", "Input data records")}
                    </Typography>
                    <Box display={"flex"} sx={(theme) => ({ paddingTop: theme.spacing(2) })}>
                        <Table
                            sourceOptions={sourceOptions}
                            sourceParameters={testCapabilities.testWithParameters.sourceParameters}
                            data={dataRecords}
                            cellErrors={cellErrors}
                            onRowUpdated={handleRowUpdated}
                            onRowAdded={handleRowAdded}
                            onRowsDeleted={handleRowsDeleted}
                            onRowMoved={handleRowMoved}
                            defaultDataRecord={defaultDataRecord}
                            recordsToAddLimitExceeded={recordsToAddLimitExceeded}
                        />
                    </Box>

                    {!recordsToAddLimitExceeded && (
                        <AppendFromLiveDataButton
                            handleGenerateTestData={handleGenerateTestData}
                            maxTestingRecords={maxTestingRecords}
                            currentRecordsNumber={dataRecords.length}
                            recordsToAddLimitExceeded={recordsToAddLimitExceeded}
                        />
                    )}
                    {recordsToAddLimitExceeded && <LimitExceededWarning maxTestingRecords={maxTestingRecords} />}
                </Box>
            </ContentSize>
        </WindowContent>
    );
}
export default Dialog;
