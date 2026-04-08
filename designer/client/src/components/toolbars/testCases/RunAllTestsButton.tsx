import { Button } from "@mui/material";
import SvgIcon from "@mui/material/SvgIcon/SvgIcon";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";

import TestingIcon from "../../../assets/img/toolbarButtons/test.svg";
import { getTestResultsLoading } from "../../../reducers/selectors/testing";
import { useAppSelector } from "../../../store/storeHelpers";
import { useTestingScenarioEnabled } from "../../modals/TestingDataRecords/useTestingScenarioEnabled";
import { useRunTestScenario } from "../test/useRunTestScenario";

export const RunAllTestsButton = () => {
    const { t } = useTranslation();
    const { runAllTests } = useRunTestScenario();
    const testingEnabled = useTestingScenarioEnabled({ disabled: false });
    const isLoading = useAppSelector(getTestResultsLoading);

    const handleClick = useCallback(
        (e: React.MouseEvent) => {
            e.stopPropagation();
            runAllTests();
        },
        [runAllTests],
    );

    return (
        <Button
            size={"small"}
            onClick={handleClick}
            disabled={!testingEnabled || isLoading}
            startIcon={
                <SvgIcon sx={{ fontSize: "14px !important" }}>
                    <TestingIcon />
                </SvgIcon>
            }
            sx={{ fontSize: 11, py: 0, px: 0.75, minWidth: 0, lineHeight: 1.5, textTransform: "none", mr: 1 }}
        >
            {t("testCases.runAll", "Run all")}
        </Button>
    );
};
