import AddIcon from "@mui/icons-material/Add";
import CheckIcon from "@mui/icons-material/Check";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import { Box, Button, Divider, Tooltip, Typography } from "@mui/material";
import React, { useCallback, useMemo, useRef, useState } from "react";
import { usePromise } from "rooks";
import { useTranslation } from "react-i18next";

import { TestCapabilityStatus } from "../../../../../../common/TestResultUtils";
import HttpService from "../../../../../../http/HttpService/instance";
import { getMaxTestingRecords } from "../../../../../../reducers/selectors/settings";
import { getInputDataRecordsForSingleSource } from "../../../../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../../../../store/storeHelpers";
import type { NodeType } from "../../../../../../types/node";
import { AppendFromLiveDataButton } from "../../../../../modals/TestingDataRecords/AppendFromLiveDataButton";
import { LimitExceededWarning } from "../../../../../modals/TestingDataRecords/LimitExceededWarning";
import { PasteRecordsButton } from "../../../../../modals/TestingDataRecords/PasteRecordsButton";
import { Table } from "../../../../../modals/TestingDataRecords/Table";
import type { TestingDataRecords } from "../../../../../modals/TestingDataRecords/types";
import { useDataRecordsActions } from "../../../../../modals/TestingDataRecords/useDataRecordsActions";
import { buildDefaultVariables } from "../../../../../modals/TestingDataRecords/utils";
import { getProcessName, getProcessProperties } from "../../../NodeDetailsContent/selectors";
import { cleanProperties } from "../../../requestSourceAddons";
import { StyledStack } from "./components/Styled";
import { TestingExpandable } from "./components/TestingExpandable";

interface Props {
    node: NodeType;
}

export const InputDataRecords = ({ node }: Props) => {
    const { t } = useTranslation();
    const [isExpanded, setIsExpanded] = useState(true);
    const maxTestingRecords = useAppSelector(getMaxTestingRecords);
    const testingDataRecordsForSource = useAppSelector((state) => getInputDataRecordsForSingleSource(state, node.id));
    const scenarioProperties = useAppSelector(getProcessProperties);
    const scenarioName = useAppSelector(getProcessName);

    const { data: sourceParameters } = usePromise(async () => {
        const { data } = await HttpService.getSourceTestCapabilities(scenarioName, scenarioProperties, cleanProperties(node));
        const capabilities = data?.testWithParameters;
        return capabilities?.status === TestCapabilityStatus.AVAILABLE ? capabilities?.sourceParameters : null;
    }, [scenarioName, scenarioProperties, node]);

    const {
        cellErrors,
        recordsErrors,
        handleRowAdded,
        handleRowsAdded,
        handleRowMoved,
        handleRowsDeleted,
        handleRowUpdated,
        generateTestDataForSingleSource,
    } = useDataRecordsActions(node, scenarioProperties);

    const defaultDataRecord = useMemo(
        () => ({
            sourceId: node.id,
            timestamp: undefined,
            variables: buildDefaultVariables(sourceParameters?.parameters),
        }),
        [node.id, sourceParameters],
    );

    const recordsToAddLimitExceeded = useMemo(() => recordsErrors.some((e) => e.type === "TEST_DATA_LIMIT_EXCEEDED"), [recordsErrors]);

    const handleGenerateTestDataForSingleSource = useCallback(
        async (numberOfSamples: number) => {
            await generateTestDataForSingleSource(numberOfSamples, scenarioProperties, node);
        },
        [generateTestDataForSingleSource, node, scenarioProperties],
    );

    const handleAddRecord = useCallback(() => {
        const records = testingDataRecordsForSource ?? [];
        handleRowAdded(records.length, {
            sourceId: defaultDataRecord.sourceId ?? node.id,
            variables: defaultDataRecord.variables ?? "",
        });
    }, [handleRowAdded, testingDataRecordsForSource, defaultDataRecord, node.id]);

    const handleClearAll = useCallback(() => {
        const indices = (testingDataRecordsForSource ?? []).map((_, i) => i);
        if (indices.length === 0) return;
        handleRowsDeleted(indices);
    }, [handleRowsDeleted, testingDataRecordsForSource]);

    const [copied, setCopied] = useState(false);
    const copyTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    const handleCopyAll = useCallback(() => {
        const records = testingDataRecordsForSource ?? [];
        const text = records
            .map((r) => {
                try {
                    return JSON.stringify(JSON.parse(r.variables));
                } catch {
                    return r.variables;
                }
            })
            .join("\n");
        navigator.clipboard.writeText(text).then(() => {
            setCopied(true);
            if (copyTimeoutRef.current) clearTimeout(copyTimeoutRef.current);
            copyTimeoutRef.current = setTimeout(() => setCopied(false), 2000);
        });
    }, [testingDataRecordsForSource]);

    const hasRecords = (testingDataRecordsForSource ?? []).length > 0;
    const addRecordDisabled = recordsToAddLimitExceeded;

    return (
        <StyledStack>
            <TestingExpandable
                componentId={"inputDataRecords"}
                expandableTitle={"Test data"}
                expanded={isExpanded}
                onChange={setIsExpanded}
            >
                {hasRecords ? (
                    <Table
                        cellErrors={cellErrors}
                        defaultDataRecord={defaultDataRecord}
                        onRowMoved={handleRowMoved}
                        onRowsDeleted={handleRowsDeleted}
                        onRowUpdated={handleRowUpdated}
                        data={testingDataRecordsForSource}
                        toolbar={
                            <Box
                                display="flex"
                                alignItems="center"
                                sx={(theme) => ({
                                    pl: 2,
                                    pr: 1,
                                    py: 0.5,
                                    borderBottom: `1px solid ${theme.palette.divider}`,
                                })}
                            >
                                <Box display="flex" alignItems="center" gap={1.5}>
                                    <Tooltip title={t("testRecords.addRecord.hint", "Add a new empty record to edit manually")}>
                                        <span>
                                            <Button
                                                size="small"
                                                variant="text"
                                                startIcon={<AddIcon />}
                                                onClick={handleAddRecord}
                                                disabled={addRecordDisabled}
                                                sx={{ textTransform: "none" }}
                                            >
                                                {t("testRecords.addRecord", "Add record")}
                                            </Button>
                                        </span>
                                    </Tooltip>
                                    <Tooltip
                                        title={t(
                                            "testRecords.pasteRecords.hint",
                                            "Paste one or more records as JSON (single object, array, or one object per line)",
                                        )}
                                    >
                                        <span>
                                            <PasteRecordsButton
                                                sourceId={node.id}
                                                onRowsAdded={handleRowsAdded}
                                                defaultVariables={defaultDataRecord?.variables}
                                                disabled={recordsToAddLimitExceeded}
                                            />
                                        </span>
                                    </Tooltip>
                                </Box>
                                <Divider orientation="vertical" flexItem sx={{ mx: 1 }} />
                                <Tooltip
                                    title={t(
                                        "testRecords.appendFromLiveData.hint",
                                        "Capture records from a live topic and append them to the list",
                                    )}
                                >
                                    <span>
                                        <AppendFromLiveDataButton
                                            handleGenerateTestData={handleGenerateTestDataForSingleSource}
                                            maxTestingRecords={maxTestingRecords}
                                            recordsToAddLimitExceeded={recordsToAddLimitExceeded}
                                        />
                                    </span>
                                </Tooltip>
                                <Box flex={1} />
                                <Divider orientation="vertical" flexItem sx={{ mx: 1, borderColor: "text.disabled" }} />
                                <Tooltip title={t("testRecords.copyAll.hint", "Copy all records as JSON lines to clipboard")}>
                                    <Button
                                        size="small"
                                        variant="text"
                                        startIcon={copied ? <CheckIcon /> : <ContentCopyIcon />}
                                        onClick={handleCopyAll}
                                        sx={{ textTransform: "none", color: "text.disabled" }}
                                    >
                                        {copied ? t("testRecords.copyAll.copied", "Copied!") : t("testRecords.copyAll", "Copy all")}
                                    </Button>
                                </Tooltip>
                                <Tooltip title={t("testRecords.clearAll.hint", "Remove all test records")}>
                                    <Button
                                        size="small"
                                        variant="text"
                                        startIcon={<DeleteOutlineIcon />}
                                        onClick={handleClearAll}
                                        sx={{ textTransform: "none", color: "text.disabled" }}
                                    >
                                        {t("testRecords.clearAll", "Clear all")}
                                    </Button>
                                </Tooltip>
                            </Box>
                        }
                    />
                ) : (
                    <Box display="flex" flexDirection="column" alignItems="center" py={5} px={3} gap={2}>
                        <Typography variant="body2" color="textSecondary" align="center" sx={{ whiteSpace: "pre-line" }}>
                            {t(
                                "testRecords.empty",
                                "No test records yet.\nAdd records manually, paste JSON lines, or append from a live topic.",
                            )}
                        </Typography>
                        <Box display="flex" flexWrap="wrap" gap={1} justifyContent="center" alignItems="center">
                            <Button
                                size="small"
                                variant="outlined"
                                startIcon={<AddIcon />}
                                onClick={handleAddRecord}
                                disabled={addRecordDisabled}
                                sx={{ textTransform: "none" }}
                            >
                                {t("testRecords.addRecord", "Add record")}
                            </Button>
                            <PasteRecordsButton
                                sourceId={node.id}
                                onRowsAdded={handleRowsAdded}
                                defaultVariables={defaultDataRecord?.variables}
                                disabled={recordsToAddLimitExceeded}
                                variant="outlined"
                            />
                            <AppendFromLiveDataButton
                                handleGenerateTestData={handleGenerateTestDataForSingleSource}
                                maxTestingRecords={maxTestingRecords}
                                recordsToAddLimitExceeded={recordsToAddLimitExceeded}
                            />
                        </Box>
                    </Box>
                )}
                {recordsToAddLimitExceeded ? <LimitExceededWarning maxTestingRecords={maxTestingRecords} /> : null}
            </TestingExpandable>
        </StyledStack>
    );
};
