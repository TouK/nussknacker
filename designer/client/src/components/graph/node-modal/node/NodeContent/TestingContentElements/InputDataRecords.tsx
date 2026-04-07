import React, { useCallback, useEffect, useMemo, useState } from "react";

import { TestCapabilityStatus } from "../../../../../../common/TestResultUtils";
import type { TestFormParameters } from "../../../../../../common/TestResultUtils";
import HttpService from "../../../../../../http/HttpService/instance";
import { getMaxTestingRecords } from "../../../../../../reducers/selectors/settings";
import { getInputDataRecordsForSingleSource } from "../../../../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../../../../store/storeHelpers";
import type { NodeType } from "../../../../../../types/node";
import { Expandable } from "../../../../../common/Expandable";
import { AppendFromLiveDataButton } from "../../../../../modals/TestingDataRecords/AppendFromLiveDataButton";
import { LimitExceededWarning } from "../../../../../modals/TestingDataRecords/LimitExceededWarning";
import { Table } from "../../../../../modals/TestingDataRecords/Table";
import { useDataRecordsActions } from "../../../../../modals/TestingDataRecords/useDataRecordsActions";
import { buildDefaultVariables } from "../../../../../modals/TestingDataRecords/utils";
import { getProcessName, getProcessProperties } from "../../../NodeDetailsContent/selectors";
import { ContentSize } from "../../ContentSize";
import { StyledStack } from "./components/Styled";

interface Props {
    node: NodeType;
}

export const InputDataRecords = ({ node }: Props) => {
    const [isExpanded, setIsExpanded] = useState(true);
    const maxTestingRecords = useAppSelector(getMaxTestingRecords);
    const testingDataRecordsForSource = useAppSelector((state) => getInputDataRecordsForSingleSource(state, node.id));
    const scenarioProperties = useAppSelector(getProcessProperties);
    const scenarioName = useAppSelector(getProcessName);

    const [sourceParameters, setSourceParameters] = useState<TestFormParameters | undefined>(undefined);

    useEffect(() => {
        HttpService.getSourceTestCapabilities(scenarioName, scenarioProperties, node).then(({ data }) => {
            const capabilities = data.testWithParameters;
            setSourceParameters(capabilities.status === TestCapabilityStatus.AVAILABLE ? capabilities : undefined);
        });
    }, [scenarioName, scenarioProperties, node]);

    const {
        cellErrors,
        recordsErrors,
        handleRowAdded,
        handleRowMoved,
        handleRowsDeleted,
        handleRowUpdated,
        generateTestDataForSingleSource,
    } = useDataRecordsActions(node, scenarioProperties);

    const defaultDataRecord = useMemo(
        () => ({
            sourceId: node.id,
            variables: buildDefaultVariables(sourceParameters?.parameters),
        }),
        [node.id, sourceParameters],
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
                        defaultRecord={defaultDataRecord}
                        onRowAdded={handleRowAdded}
                        onRowMoved={handleRowMoved}
                        onRowsDeleted={handleRowsDeleted}
                        onRowUpdated={handleRowUpdated}
                        data={testingDataRecordsForSource}
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
