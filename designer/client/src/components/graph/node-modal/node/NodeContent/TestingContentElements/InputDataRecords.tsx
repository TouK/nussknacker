import React, { useCallback, useMemo, useState } from "react";

import { getTestParameters } from "../../../../../../reducers/selectors/graph";
import { getMaxTestingRecords } from "../../../../../../reducers/selectors/settings";
import { getInputDataRecordsForSingleSource } from "../../../../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../../../../store/storeHelpers";
import type { NodeType } from "../../../../../../types/node";
import { Expandable } from "../../../../../common/Expandable";
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
    const [isExpanded, setIsExpanded] = useState(true);
    const maxTestingRecords = useAppSelector(getMaxTestingRecords);
    const testingDataRecordsForSource = useAppSelector((state) => getInputDataRecordsForSingleSource(state, sourceId));

    const scenarioProperties = useAppSelector(getProcessProperties);

    const {
        cellErrors,
        recordsErrors,
        handleRowMoved,
        handleRowsDeleted,
        handleRowUpdated,
        addDefaultRecord,
        generateTestDataForSingleSource,
    } = useDataRecordsActions(node, scenarioProperties);

    const allSourceParameters = useAppSelector(getTestParameters);
    const sourceParameters = allSourceParameters.find((sp) => sp.sourceId === sourceId);

    const onRowAppended = useCallback(
        () => addDefaultRecord(sourceId, node, scenarioProperties),
        [addDefaultRecord, sourceId, node, scenarioProperties],
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
            <Expandable componentId={"inputDataRecords"} expandableTitle={"Test data"} expanded={isExpanded} onChange={setIsExpanded}>
                <ContentSize sx={{ padding: 0, maxHeight: "45cqh", mb: 2 }}>
                    <Table
                        cellErrors={cellErrors}
                        sourceParameters={sourceParameters}
                        onRowAppended={onRowAppended}
                        onRowMoved={handleRowMoved}
                        onRowsDeleted={handleRowsDeleted}
                        onRowUpdated={handleRowUpdated}
                        data={testingDataRecordsForSource}
                        sourceOptions={[sourceId]}
                        node={node}
                        processProperties={scenarioProperties}
                    />
                </ContentSize>
                {recordsToAddLimitExceeded ? <LimitExceededWarning maxTestingRecords={maxTestingRecords} /> : null}

                <AppendFromLiveDataButton
                    handleGenerateTestData={handleGenerateTestDataForSingleSource}
                    maxTestingRecords={maxTestingRecords}
                    recordsToAddLimitExceeded={recordsToAddLimitExceeded}
                />
            </Expandable>
        </StyledStack>
    );
};
