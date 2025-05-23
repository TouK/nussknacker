import i18next from "i18next";
import React, { useCallback } from "react";
import { Scrollbars } from "react-custom-scrollbars";
import { useSelector } from "react-redux";
import { v4 as uuid4 } from "uuid";

import ProcessUtils from "../../common/ProcessUtils";
import {
    getValidationResult,
    hasNeitherErrorsNorWarnings as _hasNeitherErrorsNorWarnings,
    hasNoErrors as _hasNoErrors,
    hasNoWarnings as _hasNoWarnings,
    isValidationResultPresent as _isValidationResultPresent,
} from "../../common/ProcessUtilsAsSelectors";
import { getScenario, getTestResults } from "../../reducers/selectors/graph";
import { getUi } from "../../reducers/selectors/ui";
import type { NodeType } from "../../types";
import { useWindows } from "../../windowManager";
import type { ToolbarPanelProps } from "../toolbarComponents/ButtonsToolbar";
import { ToolbarWrapper } from "../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import Errors from "./error/Errors";
import { TipPanelStyled } from "./Styled";
import ValidTips from "./ValidTips";
import Warnings from "./Warnings";

export default function Tips(props: ToolbarPanelProps): JSX.Element {
    const { openNodeWindow } = useWindows();
    const scenario = useSelector(getScenario);

    const showDetails = useCallback(
        (event: React.MouseEvent, node: NodeType) => {
            event.preventDefault();
            openNodeWindow(node, scenario);
        },
        [openNodeWindow, scenario],
    );

    const { isToolTipsHighlighted: isHighlighted } = useSelector(getUi);
    const testResults = useSelector(getTestResults);
    const hasNeitherErrorsNorWarnings = useSelector(_hasNeitherErrorsNorWarnings);
    const { errors, warnings } = useSelector(getValidationResult);
    const isValidationResultPresent = useSelector(_isValidationResultPresent);
    const hasNoErrors = useSelector(_hasNoErrors);
    const hasNoWarnings = useSelector(_hasNoWarnings);

    return (
        <ToolbarWrapper {...props} title={i18next.t("panels.tips.title", "Tips")}>
            <TipPanelStyled id="tipsPanel" isHighlighted={isHighlighted}>
                <Scrollbars
                    style={{
                        borderRadius: 3,
                        position: "relative",
                    }}
                    renderThumbVertical={(props) => <div key={uuid4()} {...props} />}
                    hideTracksWhenNotNeeded={true}
                >
                    <ValidTips
                        loading={!isValidationResultPresent}
                        testing={!!testResults}
                        hasNeitherErrorsNorWarnings={hasNeitherErrorsNorWarnings}
                    />
                    {!hasNoErrors && <Errors errors={errors} showDetails={showDetails} scenario={scenario} />}
                    {!hasNoWarnings && (
                        <Warnings
                            warnings={ProcessUtils.extractInvalidNodes(warnings.invalidNodes)}
                            showDetails={showDetails}
                            scenarioGraph={scenario.scenarioGraph}
                        />
                    )}
                </Scrollbars>
            </TipPanelStyled>
        </ToolbarWrapper>
    );
}
