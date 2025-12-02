import { css, cx } from "@emotion/css";
import { Typography } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import { isEmpty } from "lodash";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import HttpService from "../../http/HttpService/instance";
import { getProcessName, getScenarioGraph } from "../../reducers/selectors/graph";
import { getFeatureSettings } from "../../reducers/selectors/settings";
import { useAppSelector } from "../../store/storeHelpers";
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

function GenerateTestDataDialog(props: WindowContentProps): React.JSX.Element {
    const { t } = useTranslation();
    const processName = useAppSelector(getProcessName);
    const scenarioGraph = useAppSelector(getScenarioGraph);
    const maxSize = useAppSelector(getFeatureSettings).testDataSettings.maxSamplesCount;

    const [{ testSampleSize }, setState] = useState({
        //TODO: current validators work well only for string values
        testSampleSize: "10",
    });

    const confirmAction = useCallback(async () => {
        await HttpService.generateTestData(processName, scenarioGraph, parseInt(testSampleSize));
        props.close();
    }, [processName, testSampleSize, scenarioGraph, props]);

    const validators = [literalIntegerValueValidator, minimalNumberValidator(0), maximalNumberValidator(maxSize), mandatoryValueValidator];
    const errors = extendErrors([], testSampleSize, "testData", validators);
    const isValid = isEmpty(errors);

    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => props.close(), classname: LoadingButtonTypes.secondaryButton },
            { title: t("dialog.button.ok", "Ok"), disabled: !isValid, action: () => confirmAction() },
        ],
        [t, confirmAction, props, isValid],
    );

    return (
        <PromptContent {...props} buttons={buttons}>
            <NodeTable className={cx("modalContentDark", css({ minWidth: 400 }))}>
                <Typography variant={"h6"}>{t("test-generate.title", "Generate test data")}</Typography>
                <div className={nodeValue}>
                    <NodeInput
                        value={testSampleSize}
                        onChange={(event) => setState({ testSampleSize: event.target.value })}
                        className={nodeInput}
                        autoFocus
                    />
                    <ValidationLabels fieldErrors={getValidationErrorsForField(errors, "testData")} />
                </div>
            </NodeTable>
        </PromptContent>
    );
}

export default GenerateTestDataDialog;
