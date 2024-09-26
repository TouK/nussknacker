import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import { getTestParameters } from "../../../../reducers/selectors/graph";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { useWindows, WindowKind } from "../../../../windowManager";
import { GenericActionData, GenericActionViewParams } from "../../../modals/GenericAction/GenericActionDialog";
import { useTestWithFormAvailability } from "../../../modals/GenericAction/useTestWithFormAvailability";
import { useTestWithFormAction } from "../../../modals/GenericAction/useTestWithFormAction";
import { CustomButtonTypes, PropsOfButton } from "../../../toolbarSettings/buttons";
import UrlIcon from "../../../UrlIcon";
import loadable from "@loadable/component";

export type TestWithFormButtonProps = {
    name?: string;
    title?: string;
    icon?: string;
    docs?: GenericActionViewParams["docs"];
    markdownContent?: GenericActionViewParams["markdownContent"];
};

const TestWFormIcon = loadable(() => import("../../../../assets/img/toolbarButtons/test-with-form.svg"));

function TestWithFormButton({ disabled, name, title, icon, docs, markdownContent, type }: PropsOfButton<CustomButtonTypes.testWithForm>) {
    const { t } = useTranslation();
    const { open, inform } = useWindows();

    const isAvailable = useTestWithFormAvailability(disabled);

    const testParameters = useSelector(getTestParameters);
    const sourcesFound = testParameters.length;

    const multipleSourcesTest = useCallback(() => {
        inform({ text: `Ad hoc testing is supported only for scenario with single source. Your scenario has ${sourcesFound} sources.` });
    }, [inform, sourcesFound]);

    const Icon = useCallback((props) => <UrlIcon src={icon} FallbackComponent={TestWFormIcon} {...props} />, [icon]);

    const action = useTestWithFormAction();
    const oneSourceTest = useCallback(() => {
        open<GenericActionData>({
            title: t("dialog.title.testWithForm", "Test scenario"),
            isResizable: true,
            kind: WindowKind.genericAction,
            meta: {
                view: { Icon, docs, markdownContent, confirmText: "Test" },
                action,
            },
        });
    }, [Icon, action, docs, markdownContent, open, t]);

    return (
        <ToolbarButton
            name={name || t("panels.actions.test-with-form.button.name", "ad hoc")}
            title={title || t("panels.actions.test-with-form.button.title", "run test on ad hoc data")}
            icon={<Icon />}
            disabled={!isAvailable}
            onClick={sourcesFound > 1 ? multipleSourcesTest : oneSourceTest}
            type={type}
        />
    );
}

export default TestWithFormButton;
