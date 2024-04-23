import { css, cx } from "@emotion/css";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { getScenarioGraph } from "../../reducers/selectors/graph";
import { getFeatureSettings } from "../../reducers/selectors/settings";
import { PromptContent } from "../../windowManager";
import {
    extendErrors,
    getValidationErrorsForField,
    literalIntegerValueValidator,
    mandatoryValueValidator,
    maximalNumberValidator,
    minimalNumberValidator,
} from "../graph/node-modal/editors/Validators";
import { NodeInput } from "../FormElements";
import ValidationLabels from "./ValidationLabels";
import { testScenarioWithGeneratedData } from "../../actions/nk/displayTestResults";
import { isEmpty } from "lodash";
import { getProcessName } from "../graph/node-modal/NodeDetailsContent/selectors";
import { Typography } from "@mui/material";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";

function GenerateDataAndTestDialog(props: WindowContentProps): JSX.Element {
    const { t } = useTranslation();
    const dispatch = useDispatch();
    const processName = useSelector(getProcessName);
    const scenarioGraph = useSelector(getScenarioGraph);
    const maxSize = useSelector(getFeatureSettings).testDataSettings.maxSamplesCount;

    const [{ testSampleSize }, setState] = useState({
        testSampleSize: "10",
    });

    const confirmAction = useCallback(async () => {
        await dispatch(testScenarioWithGeneratedData(testSampleSize, processName, scenarioGraph));
        props.close();
    }, [dispatch, processName, scenarioGraph, props, testSampleSize]);

    const validators = [literalIntegerValueValidator, minimalNumberValidator(0), maximalNumberValidator(maxSize), mandatoryValueValidator];
    const errors = extendErrors([], testSampleSize, "testData", validators);
    const isValid = isEmpty(errors);

    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => props.close(), classname: LoadingButtonTypes.secondaryButton },
            { title: t("dialog.button.test", "Test"), disabled: !isValid, action: () => confirmAction() },
        ],
        [t, confirmAction, props, isValid],
    );

    return (
        <PromptContent {...props} buttons={buttons}>
            <div className={cx("modalContentDark", css({ minWidth: 400 }))}>
                <Typography variant={"h3"}>{t("generate-and-test.title", "Generate scenario test data and run tests")}</Typography>
                <NodeInput
                    value={testSampleSize}
                    onChange={(event) => setState({ testSampleSize: event.target.value })}
                    className={css({
                        minWidth: "100%",
                    })}
                    autoFocus
                />
                <ValidationLabels fieldErrors={getValidationErrorsForField(errors, "testData")} />
            </div>
        </PromptContent>
    );
}

export default GenerateDataAndTestDialog;
