import React, { useMemo, useState } from "react";

import { getTestCapabilities } from "../../../../../../reducers/selectors/graph";
import { getMaxTestingRecords } from "../../../../../../reducers/selectors/settings";
import { getInputDataRecordsForSingleSource } from "../../../../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../../../../store/storeHelpers";
import { Expandable } from "../../../../../common/Expandable";
import { AppendFromLiveDataButton } from "../../../../../modals/TestingDataRecords/AppendFromLiveDataButton";
import { LimitExceededWarning } from "../../../../../modals/TestingDataRecords/LimitExceededWarning";
import { Table } from "../../../../../modals/TestingDataRecords/Table";
import { useDataRecordsActions } from "../../../../../modals/TestingDataRecords/useDataRecordsActions";
import { ContentSize } from "../../ContentSize";
import { StyledStack } from "./components/Styled";

interface Props {
    sourceId: string;
}

export const InputDataRecords = ({ sourceId }: Props) => {
    const [isExpanded, setIsExpanded] = useState(true);
    const maxTestingRecords = useAppSelector(getMaxTestingRecords);
    const testingDataRecords = useAppSelector((state) => getInputDataRecordsForSingleSource(state, sourceId));

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
        <StyledStack>
            <Expandable
                componentId={"inputDataRecords"}
                expandableTitle={"Input data records"}
                expanded={isExpanded}
                onChange={setIsExpanded}
            >
                <ContentSize sx={{ padding: 0, maxHeight: "45cqh", mb: 2 }}>
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
            </Expandable>
        </StyledStack>
    );
};
