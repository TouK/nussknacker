import React, { useState } from "react";

import { getMaxTestingRecords } from "../../../reducers/selectors/settings";
import { getTestData } from "../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../store/storeHelpers";

type RecordError = { type: "TEST_DATA_LIMIT_EXCEEDED" };

export const useDataRecordsValidation = () => {
    const maxTestingRecords = useAppSelector(getMaxTestingRecords);
    const testingDataRecords = useAppSelector(getTestData);

    const [recordsErrors, setRecordsErrors] = useState<RecordError[]>([]);

    const validateForCount = React.useCallback(
        (nextCount: (currentCount: number) => number) => {
            const testDataLimitExceeded = nextCount(testingDataRecords.length) > maxTestingRecords;
            const errors: RecordError[] = [];

            if (testDataLimitExceeded) {
                errors.push({ type: "TEST_DATA_LIMIT_EXCEEDED" });
            }

            setRecordsErrors(errors);
            return errors.length === 0;
        },
        [maxTestingRecords, testingDataRecords.length],
    );

    return { recordsErrors, validateForCount };
};
