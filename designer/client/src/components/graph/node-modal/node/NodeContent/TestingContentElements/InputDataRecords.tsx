import { Typography } from "@mui/material";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";

import { getTestCapabilities } from "../../../../../../reducers/selectors/graph";
import { getMaxTestingRecords } from "../../../../../../reducers/selectors/settings";
import { getInputDataRecordsForSingleSource } from "../../../../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../../../../store/storeHelpers";
import type { NodeType } from "../../../../../../types/node";
import { AppendFromLiveDataButton } from "../../../../../modals/TestingDataRecords/AppendFromLiveDataButton";
import { LimitExceededWarning } from "../../../../../modals/TestingDataRecords/LimitExceededWarning";
import { Table } from "../../../../../modals/TestingDataRecords/Table";
import { useDataRecordsActions } from "../../../../../modals/TestingDataRecords/useDataRecordsActions";
import { getProcessProperties } from "../../../NodeDetailsContent/selectors";
import { ContentSize } from "../../ContentSize";
import { StyledStack } from "./components/Styled";

interface Props {
    node: NodeType;
    sourceId: string;
}

export const InputDataRecords = ({ node, sourceId }: Props) => {
    const { t } = useTranslation();
    const maxTestingRecords = useAppSelector(getMaxTestingRecords);
    const testingDataRecordsForSource = useAppSelector((state) => getInputDataRecordsForSingleSource(state, sourceId));

    const {
        cellErrors,
        recordsErrors,
        handleRowAdded,
        handleRowMoved,
        handleRowsDeleted,
        handleRowUpdated,
        generateTestDataForSingleSource,
    } = useDataRecordsActions();

    const testCapabilities = useAppSelector(getTestCapabilities);
    const scenarioProperties = useAppSelector(getProcessProperties);
    const defaultParameter = testCapabilities.testWithParameters.sourceParameters.find(
        (sourceParameter) => sourceParameter.sourceId === sourceId,
    );

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

    const recordsToAddLimitExceeded = useMemo(
        () => recordsErrors.some((recordsErrors) => recordsErrors.type === "TEST_DATA_LIMIT_EXCEEDED"),
        [recordsErrors],
    );

    const handleGenerateTestDataForSingleSource = useCallback(
        async (numberOfSamples: number) => {
            await generateTestDataForSingleSource(numberOfSamples, scenarioProperties, node);
        },
        [generateTestDataForSingleSource, node, scenarioProperties],
    );

    return (
        <StyledStack>
            <Typography m={0} variant="h5">
                {t("testingDialog.label.inputDataRecords", "Input data records")}
            </Typography>
            <ContentSize sx={{ padding: 0, maxHeight: "45cqh" }}>
                <Table
                    cellErrors={cellErrors}
                    defaultDataRecord={defaultDataRecord}
                    onRowAdded={handleRowAdded}
                    onRowMoved={handleRowMoved}
                    onRowsDeleted={handleRowsDeleted}
                    onRowUpdated={handleRowUpdated}
                    data={testingDataRecordsForSource}
                    sourceOptions={[sourceId]}
                    sourceParameters={testCapabilities.testWithParameters.sourceParameters}
                />
            </ContentSize>
            {recordsToAddLimitExceeded ? <LimitExceededWarning maxTestingRecords={maxTestingRecords} /> : null}
            <AppendFromLiveDataButton
                handleGenerateTestData={handleGenerateTestDataForSingleSource}
                maxTestingRecords={maxTestingRecords}
                recordsToAddLimitExceeded={recordsToAddLimitExceeded}
            />
        </StyledStack>
    );
};
