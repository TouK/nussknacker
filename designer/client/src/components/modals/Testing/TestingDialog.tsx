import { Box, Typography } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import type { ElementType, ReactElement } from "react";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { testScenarioWithEventsData } from "../../../actions/nk/displayTestResults";
import HttpService from "../../../http/HttpService";
import { getProcessName, getScenarioGraph, getTestCapabilities, getTestingEventParameters } from "../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import type { WindowKind } from "../../../windowManager";
import { WindowContent } from "../../../windowManager";
import { LoadingButtonTypes } from "../../../windowManager/LoadingButton";
import type { CellError } from "../../graph/node-modal/editors/expression/Table/errorHighlights";
import { ContentSize } from "../../graph/node-modal/node/ContentSize";
import { WindowHeaderIconStyled } from "../../graph/node-modal/nodeDetails/NodeDetailsStyled";
import { NodeDocs } from "../../graph/node-modal/nodeDetails/SubHeader";
import { AppendRowButton } from "./AppendRowButton";
import type { TestingEventParameters } from "./TestingEventsTable";
import { TestingEventsTable } from "./TestingEventsTable";
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

function TestingDialog(props: WindowContentProps<WindowKind, TestingData>): ReactElement {
    const { t } = useTranslation();
    const { data, close } = props;
    const {
        meta: { viewParams },
        kind,
    } = data;

    const dispatch = useAppDispatch();
    const testCapabilities = useAppSelector(getTestCapabilities);
    const testingEventsParameters = useAppSelector(getTestingEventParameters);
    const scenarioName = useAppSelector(getProcessName);
    const scenarioGraph = useAppSelector(getScenarioGraph);

    const defaultParameter = testCapabilities.testWithParameters.sourceParameters[0];

    const defaultEvent = useMemo(
        () =>
            defaultParameter
                ? {
                      sourceId: defaultParameter.sourceId,
                      timestamp: undefined,
                      variables: defaultParameter.parameters[0].defaultValue.expression,
                  }
                : { sourceId: undefined, timestamp: undefined, variables: undefined },
        [defaultParameter],
    );

    const [events, setEvents] = useState<TestingEventParameters[]>(testingEventsParameters || []);
    const [cellErrors, setCellErrors] = useState<CellError[]>([]);

    const handleGenerateTestData = useCallback(
        async (numberOfSamples: number) => {
            const { data } = await HttpService.generatedTestData(scenarioName, scenarioGraph, numberOfSamples);
            setEvents((prevState) => [...prevState, ...data.map(mapGeneratedTestingDataToTableFormat)]);
        },
        [scenarioGraph, scenarioName],
    );

    const sourceOptions = useMemo(
        () => testCapabilities.testWithParameters.sourceParameters.flatMap((sourceParameter) => sourceParameter.sourceId),
        [testCapabilities.testWithParameters.sourceParameters],
    );

    const validateEditedRow = React.useCallback(
        (rowIndex: number, row: TestingEventParameters) => {
            HttpService.validateTestDataWithEventsData(scenarioName, scenarioGraph, row.variables).then(({ data }) =>
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
        (rowIndex: number, row: TestingEventParameters) => {
            setEvents((prev) => {
                const next = [...prev];
                if (rowIndex >= next.length) {
                    for (let i = next.length; i <= rowIndex; i++) {
                        next[i] = { sourceId: "", timestamp: undefined, variables: "" } as TestingEventParameters;
                    }
                }
                next[rowIndex] = row;
                return next;
            });
            validateEditedRow(rowIndex, row);
        },
        [validateEditedRow],
    );

    const handleRowAdded = React.useCallback((rowIndex: number, row: TestingEventParameters) => {
        setEvents((prev) => {
            const next = [...prev];
            if (rowIndex === next.length) next.push(row);
            else next.splice(rowIndex, 0, row);
            return next;
        });
        setCellErrors((prev) => prev.map((e) => (e.y >= rowIndex ? { ...e, y: e.y + 1 } : e)));
    }, []);

    const handleRowsDeleted = React.useCallback((deletedRows: number[]) => {
        if (!deletedRows.length) return;
        const deletedSet = new Set(deletedRows);
        const sorted = [...deletedRows].sort((a, b) => a - b);
        setEvents((prev) => prev.filter((_, i) => !deletedSet.has(i)));
        setCellErrors((prev) =>
            prev
                .filter((e) => !deletedSet.has(e.y))
                .map((e) => {
                    const shift = sorted.reduce((acc, r) => (r < e.y ? acc + 1 : acc), 0);
                    return shift ? { ...e, y: e.y - shift } : e;
                }),
        );
    }, []);

    const disableTestButton = events.length === 0 || cellErrors.length > 0;
    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("testingForm.cancelButton.label", "Cancel"), action: () => close(), classname: LoadingButtonTypes.secondaryButton },
            {
                disabled: disableTestButton,
                title: t("testingForm.testButton.label", "Test"),
                action: () => {
                    try {
                        dispatch(testScenarioWithEventsData(events));
                        close();
                    } catch (e) {
                        console.error(e.message);
                    }
                },
            },
        ],
        [close, disableTestButton, dispatch, events, t],
    );

    return (
        <WindowContent
            {...props}
            icon={<WindowHeaderIconStyled as={viewParams.Icon} type={kind} />}
            subheader={<NodeDocs name={viewParams.docs?.label} href={viewParams.docs?.url} />}
            buttons={buttons}
        >
            <ContentSize>
                <Box sx={{ height: "100%", display: "flex", flexDirection: "column" }} pl={1}>
                    <Typography mt={0} variant={"h3"}>
                        {t("testingDialog.label.inputDataRecords", "Input data records")}
                    </Typography>
                    <Box display={"flex"} sx={{ padding: "2.5px" }}>
                        <TestingEventsTable
                            sourceOptions={sourceOptions}
                            sourceParameters={testCapabilities.testWithParameters.sourceParameters}
                            data={events}
                            cellErrors={cellErrors}
                            onRowUpdated={handleRowUpdated}
                            onRowAdded={handleRowAdded}
                            onRowsDeleted={handleRowsDeleted}
                            defaultEvent={defaultEvent}
                        />
                    </Box>

                    <AppendRowButton handleGenerateTestData={handleGenerateTestData} />
                </Box>
            </ContentSize>
        </WindowContent>
    );
}
export default TestingDialog;
