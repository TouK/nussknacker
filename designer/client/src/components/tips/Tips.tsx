import i18next from "i18next";
import React, { useCallback } from "react";
import { Scrollbars } from "react-custom-scrollbars";
import { useSelector } from "react-redux";
import { v4 as uuid4 } from "uuid";

import ProcessUtils from "../../common/ProcessUtils";
import { VisibleDataType } from "../../reducers/graph";
import { getVisibleDataType } from "../../reducers/selectors/getLiveData";
import { getScenario } from "../../reducers/selectors/graph";
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
    const visibleDataType = useSelector(getVisibleDataType);
    const { errors, warnings } = ProcessUtils.getValidationResult(scenario);

    return (
        <ToolbarWrapper {...props} title={i18next.t("panels.tips.title", "Tips")}>
            <TipPanelStyled id="tipsPanel" isHighlighted={isHighlighted}>
                <Scrollbars
                    style={{ borderRadius: 3, position: "relative" }}
                    renderThumbVertical={(props) => <div key={uuid4()} {...props} />}
                    hideTracksWhenNotNeeded={true}
                >
                    <ValidTips
                        loading={!ProcessUtils.isValidationResultPresent(scenario)}
                        testing={visibleDataType === VisibleDataType.test}
                        hasNeitherErrorsNorWarnings={ProcessUtils.hasNeitherErrorsNorWarnings(scenario)}
                    />
                    {!ProcessUtils.hasNoErrors(scenario) && <Errors errors={errors} showDetails={showDetails} scenario={scenario} />}
                    {!ProcessUtils.hasNoWarnings(scenario) && (
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
