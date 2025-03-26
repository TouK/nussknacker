import loadable from "@loadable/component";
import React, { useCallback, useContext } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import { getTestParameters, getTestResultsLoading } from "../../../../reducers/selectors/graph";
import { useWindows, WindowKind } from "../../../../windowManager";
import { NodeContext } from "../../../graph/node-modal/node/NodeDetails";
import type { AdhocTestingData, AdhocTestingViewParams } from "../../../modals/AdhocTesting/AdhocTestingDialog";
import { useAdhocTestingAction } from "../../../modals/AdhocTesting/useAdhocTestingAction";
import { useAdhocTestingAvailability } from "../../../modals/AdhocTesting/useAdhocTestingAvailability";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { CustomButtonTypes, PropsOfButton } from "../../../toolbarSettings/buttons";
import { ButtonProgress } from "./ButtonProgress";

export type AdhocTestingButtonProps = {
    name?: string;
    title?: string;
    docs?: AdhocTestingViewParams["docs"];
    markdownContent?: AdhocTestingViewParams["markdownContent"];
};

const AdhocTestingIcon = loadable(() => import("../../../../assets/img/toolbarButtons/test-with-form.svg"));

function AdhocTestingButton({ disabled, name, title, docs, markdownContent, type }: PropsOfButton<CustomButtonTypes.adhocTesting>) {
    const { t } = useTranslation();
    const { open, inform } = useWindows();

    const isAvailable = useAdhocTestingAvailability(disabled);

    const testParameters = useSelector(getTestParameters);
    const isLoading = useSelector(getTestResultsLoading);
    const sourcesFound = testParameters.length;

    const multipleSourcesTest = useCallback(() => {
        inform({ text: `Ad hoc testing is supported only for scenario with single source. Your scenario has ${sourcesFound} sources.` });
    }, [inform, sourcesFound]);

    const action = useAdhocTestingAction();
    const oneSourceTest = useCallback(() => {
        open<AdhocTestingData>({
            title: t("dialog.title.adhoc-testing.test", "Test scenario"),
            isResizable: true,
            kind: WindowKind.adhocTesting,
            meta: {
                view: { Icon: AdhocTestingIcon, docs, markdownContent },
                action,
            },
        });
    }, [action, docs, markdownContent, open, t]);

    const nodeContext = useContext(NodeContext);

    return (
        <ButtonProgress enabled={isLoading}>
            <ToolbarButton
                name={name || t("panels.actions.adhoc-testing.button.name", "ad hoc")}
                title={title || t("panels.actions.adhoc-testing.button.title", "run test on ad hoc data")}
                icon={<AdhocTestingIcon />}
                disabled={!isAvailable || isLoading}
                onClick={(e) => {
                    if (sourcesFound > 1) {
                        return multipleSourcesTest();
                    }
                    if (action.previousTestData && Boolean(nodeContext) != e.shiftKey) {
                        return action.onConfirmAction(action.previousTestData);
                    }
                    return oneSourceTest();
                }}
                type={type}
            />
        </ButtonProgress>
    );
}

export default AdhocTestingButton;
