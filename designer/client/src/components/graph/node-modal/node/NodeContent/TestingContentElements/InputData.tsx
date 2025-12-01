import React, { useMemo } from "react";

import { getTestCapabilities, getTestingDataRecords } from "../../../../../../reducers/selectors/graph";
import { useAppSelector } from "../../../../../../store/storeHelpers";
import { Table } from "../../../../../modals/TestingDataRecords/Table";
import { useDataRecordsTableActions } from "../../../../../modals/TestingDataRecords/useDataRecordsTableActions";
import { ContentSize } from "../../ContentSize";

export const InputData = () => {
    const testingDataRecords = useAppSelector(getTestingDataRecords);

    const { cellErrors, handleRowAdded, dataRecords, handleRowMoved, handleRowsDeleted, handleRowUpdated } = useDataRecordsTableActions({
        testingDataRecords: testingDataRecords || [],
    });
    const testCapabilities = useAppSelector(getTestCapabilities);
    const defaultParameter = testCapabilities.testWithParameters.sourceParameters[0];

    const sourceOptions = useMemo(
        () => testCapabilities.testWithParameters.sourceParameters.flatMap((sourceParameter) => sourceParameter.sourceId),
        [testCapabilities.testWithParameters.sourceParameters],
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

    return (
        <ContentSize sx={{ paddingX: 3, paddingY: 3, height: "60vh" }}>
            <Table
                cellErrors={cellErrors}
                defaultDataRecord={defaultDataRecord}
                onRowAdded={handleRowAdded}
                onRowMoved={handleRowMoved}
                onRowsDeleted={handleRowsDeleted}
                onRowUpdated={handleRowUpdated}
                data={dataRecords}
                sourceOptions={sourceOptions}
                sourceParameters={testCapabilities.testWithParameters.sourceParameters}
            />
        </ContentSize>
    );
};
