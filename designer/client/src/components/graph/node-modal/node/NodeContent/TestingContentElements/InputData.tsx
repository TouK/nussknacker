import React, { useMemo } from "react";

import { getTestCapabilities, getTestingDataRecordsForSingleSource } from "../../../../../../reducers/selectors/graph";
import { useAppSelector } from "../../../../../../store/storeHelpers";
import { Table } from "../../../../../modals/TestingDataRecords/Table";
import { useDataRecordsActions } from "../../../../../modals/TestingDataRecords/useDataRecordsActions";
import { ContentSize } from "../../ContentSize";

interface Props {
    sourceId: string;
}

export const InputData = ({ sourceId }: Props) => {
    const testingDataRecords = useAppSelector((state) => getTestingDataRecordsForSingleSource(state, sourceId));

    const { cellErrors, handleRowAdded, handleRowMoved, handleRowsDeleted, handleRowUpdated } = useDataRecordsActions({
        testingDataRecords,
    });

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

    return (
        <ContentSize sx={{ paddingX: 3, paddingY: 3, height: "60vh" }}>
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
    );
};
