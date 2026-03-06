import { Alert } from "@mui/material";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

interface Props {
    maxTestingRecords: number;
}
export const LimitExceededWarning = ({ maxTestingRecords }: Props) => {
    const { t } = useTranslation();
    const [warningVisible, setWarningVisible] = useState<boolean>(true);

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
                "The maximum number of {{maxTestingRecords}} input records has been exceeded.",
                { maxTestingRecords },
            )}
        </Alert>
    );
};
