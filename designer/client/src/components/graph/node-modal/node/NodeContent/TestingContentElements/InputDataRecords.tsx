import { Stack, Typography } from "@mui/material";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";

import { getTestCapabilities, getTestingDataRecordsForSingleSource } from "../../../../../../reducers/selectors/graph";
import { getMaxTestingRecords } from "../../../../../../reducers/selectors/settings";
import { useAppSelector } from "../../../../../../store/storeHelpers";
import { AppendFromLiveDataButton } from "../../../../../modals/TestingDataRecords/AppendFromLiveDataButton";
import { LimitExceededWarning } from "../../../../../modals/TestingDataRecords/LimitExceededWarning";
import { Table } from "../../../../../modals/TestingDataRecords/Table";
import { useDataRecordsActions } from "../../../../../modals/TestingDataRecords/useDataRecordsActions";
import { ContentSize } from "../../ContentSize";

interface Props {
    sourceId: string;
}

export const InputDataRecords = ({ sourceId }: Props) => {
    const { t } = useTranslation();
    const maxTestingRecords = useAppSelector(getMaxTestingRecords);
    const testingDataRecords = useAppSelector((state) => getTestingDataRecordsForSingleSource(state, sourceId));

    const { cellErrors, recordsErrors, handleRowAdded, handleRowMoved, handleRowsDeleted, handleRowUpdated, handleGenerateTestData } =
        useDataRecordsActions();

    const testCapabilities = useAppSelector(getTestCapabilities);
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

    return (
        <Stack p={2} gap={2}>
            <Typography m={0} variant="h5">
                {t("testingDialog.label.inputDataRecords", "Input data records")}
            </Typography>
            <ContentSize sx={{ height: "60vh", padding: 0 }}>
                <Table
                    cellErrors={cellErrors}
                    defaultDataRecord={defaultDataRecord}
                    onRowAdded={handleRowAdded}
                    onRowMoved={handleRowMoved}
                    onRowsDeleted={handleRowsDeleted}
                    onRowUpdated={handleRowUpdated}
                    data={testingDataRecords}
                    sourceOptions={[sourceId]}
                    sourceParameters={testCapabilities.testWithParameters.sourceParameters}
                />
            </ContentSize>
            {recordsToAddLimitExceeded ? <LimitExceededWarning maxTestingRecords={maxTestingRecords} /> : null}
            {/*TODO: Adjust handleGenerateTestData when the backend receives an option to generate test data for a specific source*/}
            <AppendFromLiveDataButton
                handleGenerateTestData={handleGenerateTestData}
                maxTestingRecords={maxTestingRecords}
                currentRecordsNumber={testingDataRecords.length}
                recordsToAddLimitExceeded={recordsToAddLimitExceeded}
            />
        </Stack>
    );
};
