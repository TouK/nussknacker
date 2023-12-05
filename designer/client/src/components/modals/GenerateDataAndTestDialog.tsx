import { css, cx } from "@emotion/css";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSelector, useDispatch } from "react-redux";
import { getProcessId, getProcessToDisplay } from "../../reducers/selectors/graph";
import { getFeatureSettings } from "../../reducers/selectors/settings";
import { PromptContent } from "../../windowManager";
import {
    literalIntegerValueValidator,
    mandatoryValueValidator,
    maximalNumberValidator,
    minimalNumberValidator,
} from "../graph/node-modal/editors/Validators";
import { NodeInput } from "../withFocus";
import ValidationLabels from "./ValidationLabels";
import { testScenarioWithGeneratedData } from "../../actions/nk/displayTestResults";

function GenerateDataAndTestDialog(props: WindowContentProps): JSX.Element {
    const { t } = useTranslation();
    const dispatch = useDispatch();
    const processId = useSelector(getProcessId);
    const processToDisplay = useSelector(getProcessToDisplay);
    const maxSize = useSelector(getFeatureSettings).testDataSettings.maxSamplesCount;

    const [{ testSampleSize }, setState] = useState({
        testSampleSize: "10",
    });

    const confirmAction = useCallback(async () => {
        await dispatch(testScenarioWithGeneratedData(processId, testSampleSize, processToDisplay));
        props.close();
    }, [processId, processToDisplay, props, testSampleSize]);

    const validators = [literalIntegerValueValidator, minimalNumberValidator(0), maximalNumberValidator(maxSize), mandatoryValueValidator];
    const isValid = validators.every((v) => v.isValid(testSampleSize));

    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => props.close() },
            { title: t("dialog.button.test", "Test"), disabled: !isValid, action: () => confirmAction() },
        ],
        [t, confirmAction, props, isValid],
    );

    return (
        <PromptContent {...props} buttons={buttons}>
            <div className={cx("modalContentDark", css({ minWidth: 400 }))}>
                <h3>{t("generate-and-test.title", "Generate scenario test data and run tests")}</h3>
                <NodeInput
                    value={testSampleSize}
                    onChange={(event) => setState({ testSampleSize: event.target.value })}
                    className={css({
                        minWidth: "100%",
                    })}
                    autoFocus
                />
                <ValidationLabels fieldErrors={[]} />
            </div>
        </PromptContent>
    );
}

export default GenerateDataAndTestDialog;
