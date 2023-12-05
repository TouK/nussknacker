import i18next from "i18next";
import React, { useCallback } from "react";
import { Scrollbars } from "react-custom-scrollbars";
import { useSelector } from "react-redux";
import { v4 as uuid4 } from "uuid";
import ProcessUtils from "../../common/ProcessUtils";
import { getProcessToDisplay, getTestResults } from "../../reducers/selectors/graph";
import { getUi } from "../../reducers/selectors/ui";
import { useWindows } from "../../windowManager";
import { ToolbarWrapper } from "../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import Errors from "./error/Errors";
import ValidTips from "./ValidTips";
import Warnings from "./Warnings";
import { TipPanelStyled } from "./Styled";
import { NodeType } from "../../types";

export default function Tips(): JSX.Element {
    const { openNodeWindow } = useWindows();
    const currentProcess = useSelector(getProcessToDisplay);

    const showDetails = useCallback(
        (event: React.MouseEvent, node: NodeType) => {
            event.preventDefault();
            openNodeWindow(node, currentProcess);
        },
        [openNodeWindow, currentProcess],
    );

    const { isToolTipsHighlighted: isHighlighted } = useSelector(getUi);
    const testResults = useSelector(getTestResults);
    const { errors, warnings } = ProcessUtils.getValidationResult(currentProcess);

    return (
        <ToolbarWrapper title={i18next.t("panels.tips.title", "Tips")} id="TIPS-PANEL">
            <TipPanelStyled id="tipsPanel" isHighlighted={isHighlighted}>
                <Scrollbars
                    style={{ borderRadius: 3, position: "relative" }}
                    renderThumbVertical={(props) => <div key={uuid4()} {...props} />}
                    hideTracksWhenNotNeeded={true}
                >
                    <ValidTips
                        testing={!!testResults}
                        hasNeitherErrorsNorWarnings={ProcessUtils.hasNeitherErrorsNorWarnings(currentProcess)}
                    />
                    {!ProcessUtils.hasNoErrors(currentProcess) && (
                        <Errors fieldErrors={errors} showDetails={showDetails} currentProcess={currentProcess} />
                    )}
                    {!ProcessUtils.hasNoWarnings(currentProcess) && (
                        <Warnings
                            warnings={ProcessUtils.extractInvalidNodes(warnings.invalidNodes)}
                            showDetails={showDetails}
                            currentProcess={currentProcess}
                        />
                    )}
                </Scrollbars>
            </TipPanelStyled>
        </ToolbarWrapper>
    );
}
