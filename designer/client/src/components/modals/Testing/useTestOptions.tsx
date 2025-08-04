import { useMemo } from "react";
import { useTranslation } from "react-i18next";

import DryRunTestingIcon from "../../../assets/img/icons/test-dry-run.svg";
import GenerateAndTestIcon from "../../../assets/img/icons/test-using-live-data.svg";
import { TestCapabilityStatus } from "../../../common/TestResultUtils";
import { getTestCapabilities, getTestType } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import type { CustomRadioProps } from "../../customRadio/CustomRadio";

export enum TestType {
    withParameters = "withParameters",
    withLiveData = "withLiveData",
}

export type TestingOption = CustomRadioProps & {
    disableReason: string;
    menuLabel?: string;
};

export const useTestOptions = (): {
    options: TestingOption[];
    testType: TestType;
} => {
    const { t } = useTranslation();
    const testCapabilities = useAppSelector(getTestCapabilities);
    const storedTestType = useAppSelector(getTestType);

    const options: TestingOption[] = useMemo(
        () => [
            {
                label: t("testingForm.withParameters.label", "Custom input"),
                title: t("testingForm.withParameters.title", "Enter input data for the sources manually."),
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
                title: t(
                    "testingForm.withGeneratedData.title",
                    "A specified number of samples will be retrieved from the underlying sources and used as an input data. The test will fail if no data is available.",
                ),
                value: TestType.withLiveData,
                Icon: GenerateAndTestIcon,
                disabled: testCapabilities?.testWithLiveData.status !== TestCapabilityStatus.AVAILABLE,
                disableReason: t(
                    "testingForm.withGeneratedData.disableReason",
                    "Currently configured scenario sources do not support testing with live data",
                ),
            },
        ],
        [t, testCapabilities?.testWithLiveData.status, testCapabilities?.testWithParameters.status],
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
