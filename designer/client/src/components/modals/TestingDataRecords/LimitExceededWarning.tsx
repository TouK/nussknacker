import { Alert } from "@mui/material";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

interface Props {
    maxTestingRecords: number;
    recordsToAddLimitExceeded: boolean;
}
export const LimitExceededWarning = ({ maxTestingRecords, recordsToAddLimitExceeded }: Props) => {
    const { t } = useTranslation();
    const [warningVisible, setWarningVisible] = useState<boolean>(false);

    useEffect(() => {
        if (recordsToAddLimitExceeded) {
            setWarningVisible(true);
        }
    }, [recordsToAddLimitExceeded]);

    if (!warningVisible) {
        return null;
    }

    return (
        <Alert
            sx={{ width: "100%" }}
            severity={"warning"}
            onClose={() => {
                setWarningVisible(false);
            }}
        >
            {t(
                "testingDialog.warning.dataRecordsLimitExceeded",
                "The maximum number of {{maxTestingRecords}} Input data records has been exceeded.",
                { maxTestingRecords },
            )}
        </Alert>
    );
};
