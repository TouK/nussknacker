import React, { useEffect, useState } from "react";

import { getMaxTestingRecords } from "../../../reducers/selectors/settings";
import { getTestData } from "../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../store/storeHelpers";

type RecordError = { type: "TEST_DATA_LIMIT_EXCEEDED" };

export const useDataRecordsValidation = () => {
    const maxTestingRecords = useAppSelector(getMaxTestingRecords);
    const testingDataRecords = useAppSelector(getTestData);

    const [recordsErrors, setRecordsErrors] = useState<RecordError[]>([]);

    useEffect(() => {
        if (testingDataRecords.length < maxTestingRecords) {
            setRecordsErrors([]);
        }
    }, [testingDataRecords.length, maxTestingRecords]);

    const validateForCount = React.useCallback(
        (nextCount: (currentCount: number) => number) => {
            const testDataLimitExceeded = nextCount(testingDataRecords.length) > maxTestingRecords;

            if (testDataLimitExceeded) {
                setRecordsErrors([{ type: "TEST_DATA_LIMIT_EXCEEDED" }]);
            }

            return !testDataLimitExceeded;
        },
        [maxTestingRecords, testingDataRecords.length],
    );

    const limitReached = testingDataRecords.length >= maxTestingRecords;

    return { recordsErrors, validateForCount, limitReached };
};
