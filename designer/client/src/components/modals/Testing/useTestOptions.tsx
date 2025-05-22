import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import DryRunTestingIcon from "../../../assets/img/icons/test-dry-run.svg";
import GenerateAndTestIcon from "../../../assets/img/icons/test-using-generated-data.svg";
import { TestCapabilityStatus } from "../../../common/TestResultUtils";
import { getTestCapabilities, getTestType } from "../../../reducers/selectors/graph";
import type { CustomRadioProps } from "../../customRadio/CustomRadio";

export enum TestType {
    withParameters = "withParameters",
    withGeneratedData = "withGeneratedData",
}

export type TestingOption = CustomRadioProps & {
    disableReason: string;
    menuLabel: string;
};

export const useTestOptions = (): {
    options: TestingOption[];
    testType: TestType;
} => {
    const { t } = useTranslation();
    const testCapabilities = useSelector(getTestCapabilities);
    const storedTestType = useSelector(getTestType);

    const options: TestingOption[] = useMemo(
        () => [
            {
                label: t("testingForm.withParameters.label", "Custom input"),
                menuLabel: t("testingForm.withParameters.menu.label", "Test with custom data"),
                value: TestType.withParameters,
                Icon: DryRunTestingIcon,
                disabled: testCapabilities?.testWithParameters.status !== TestCapabilityStatus.AVAILABLE,
                disableReason: t(
                    "testingForm.withParameters.disableReason",
                    "Currently configured scenario sources do not support testing with custom input",
                ),
            },
            {
                label: t("testingForm.withGeneratedData.label", "Live data"),
                menuLabel: t("testingForm.withGeneratedData.menu.label", "Test with live data"),
                value: TestType.withGeneratedData,
                Icon: GenerateAndTestIcon,
                disabled: testCapabilities?.testWithGeneratedData.status !== TestCapabilityStatus.AVAILABLE,
                disableReason: t(
                    "testingForm.withGeneratedData.disableReason",
                    "Currently configured scenario sources do not support testing with live data",
                ),
            },
        ],
        [t, testCapabilities?.testWithGeneratedData.status, testCapabilities?.testWithParameters.status],
    );

    const testType = useMemo(() => {
        const value =
            storedTestType ??
            options
                .filter(({ disabled }) => !disabled)
                .map(({ value }) => value)
                .shift();
        return value as TestType;
    }, [storedTestType, options]);

    return {
        options,
        testType,
    };
};
