import { css, cx } from "@emotion/css";
import { Typography } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import { isEmpty } from "lodash";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { testScenarioWithGeneratedData } from "../../actions/nk/testingActions";
import { getFeatureSettings } from "../../reducers/selectors/settings";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { PromptContent } from "../../windowManager/PromptContent";
import { NodeInput } from "../FormElements";
import {
    extendErrors,
    getValidationErrorsForField,
    literalIntegerValueValidator,
    mandatoryValueValidator,
    maximalNumberValidator,
    minimalNumberValidator,
} from "../graph/node-modal/editors/Validators";
import { NodeTable } from "../graph/node-modal/NodeDetailsContent/NodeTable";
import { nodeInput, nodeValue } from "../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import ValidationLabels from "./ValidationLabels";

function GenerateDataAndTestDialog(props: WindowContentProps): React.JSX.Element {
    const { t } = useTranslation();
    const dispatch = useAppDispatch();
    const maxSize = useAppSelector(getFeatureSettings).testDataSettings.maxSamplesCount;

    const [{ testSampleSize }, setState] = useState({
        testSampleSize: "10",
    });

    const confirmAction = useCallback(async () => {
        await dispatch(testScenarioWithGeneratedData(testSampleSize));
        props.close();
    }, [dispatch, props, testSampleSize]);

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
            <NodeTable className={cx("modalContentDark", css({ minWidth: 400 }))}>
                <Typography variant={"h6"}>{t("generate-and-test.title", "Generate scenario test data and run tests")}</Typography>
                <div className={nodeValue}>
                    <NodeInput
                        value={testSampleSize}
                        onChange={(event) => setState({ testSampleSize: event.target.value })}
                        className={nodeInput}
                        autoFocus
                    />
                </div>
                <ValidationLabels fieldErrors={getValidationErrorsForField(errors, "testData")} />
            </NodeTable>
        </PromptContent>
    );
}

export default GenerateDataAndTestDialog;
