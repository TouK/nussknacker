import loadable from "@loadable/component";
import type { WindowContentProps } from "@touk/window-manager";
import React from "react";
import { ErrorBoundary } from "react-error-boundary";

import { DialogErrorFallbackComponent } from "../components/common/error-boundary/fallbackComponent/DialogErrorFallbackComponent";
import { DialogWithChildren } from "../components/DialogWithChildren";
import FrameDialog from "../components/FrameDialog";
import { MeasuresDialogContent } from "../components/graph/node-modal/editors/expression/FlinkSqlTemplateEditor/cep/MeasuresDialogContent";
import AddAttachmentDialog from "../components/modals/AddAttachmentDialog";
import RemoteModuleDialog from "../components/RemoteModuleDialog";
import LoaderSpinner from "../components/spinner/Spinner";
import { Debug } from "../containers/Debug";
import { NuThemeProvider } from "../containers/theme/nuThemeProvider";
import { WindowContent } from "./WindowContent";
import { WindowKind } from "./WindowKind";

const AddProcessDialog = loadable(() => import("../components/AddProcessDialog"), { fallback: <LoaderSpinner show /> });
const NodeDetails = loadable(() => import("../components/graph/node-modal/node/NodeDetails"), {
    fallback: <LoaderSpinner show />,
});
const DescriptionDialog = loadable(() => import("../components/graph/node-modal/node/DescriptionDialog"), {
    fallback: <LoaderSpinner show />,
});
const CountsDialog = loadable(() => import("../components/modals/CalculateCounts/CalculateCountsDialog"), {
    fallback: <LoaderSpinner show />,
});
const CompareVersionsDialog = loadable(() => import("../components/modals/CompareVersionsDialog"), {
    fallback: <LoaderSpinner show />,
});
const AdhocTestingDialog = loadable(() => import("../components/modals/AdhocTesting/AdhocTestingDialog"), {
    fallback: <LoaderSpinner show />,
});
const DeployProcessDialog = loadable(() => import("../components/modals/DeployProcessDialog"), {
    fallback: <LoaderSpinner show />,
});
const DeployWithParametersDialog = loadable(() => import("../components/modals/DeployWithParameters/DeployWithParametersDialog"), {
    fallback: <LoaderSpinner show />,
});
const GenericConfirmDialog = loadable(() => import("../components/modals/GenericConfirmDialog"), {
    fallback: <LoaderSpinner show />,
});
const GenericInfoDialog = loadable(() => import("../components/modals/GenericInfoDialog"), {
    fallback: <LoaderSpinner show />,
});
const SaveProcessDialog = loadable(() => import("../components/modals/saveScenario/SaveScenarioDialog"), {
    fallback: <LoaderSpinner show />,
});
const GenerateTestDataDialog = loadable(() => import("../components/modals/GenerateTestDataDialog"), {
    fallback: <LoaderSpinner show />,
});
const GenerateDataAndTestDialog = loadable(() => import("../components/modals/GenerateDataAndTestDialog"), {
    fallback: <LoaderSpinner show />,
});

const ScenarioDetailsDialog = loadable(() => import("../components/modals/MoreScenarioDetailsDialog"), {
    fallback: <LoaderSpinner show />,
});

const AddCommentDialog = loadable(() => import("../components/modals/AddCommentDialog"), {
    fallback: <LoaderSpinner show />,
});

const ModifyActivityCommentDialog = loadable(() => import("../components/modals/ModifyActivityCommentDialog"), {
    fallback: <LoaderSpinner show />,
});

const PropertiesDialog = loadable(() => import("../components/modals/PropertiesDialog"), {
    fallback: <LoaderSpinner show />,
});

const AiAssistantModal = loadable(() => import("../components/aiAssistant/components/AiAssistantModal"), {
    fallback: <LoaderSpinner show />,
});

const EnterpriseFeatureInfo = loadable(
    () => import("../components/graph/node-modal/node/NodeContent/TestingContentElements/EnterpriseFeatureInfo"),
    {
        fallback: <LoaderSpinner show />,
    },
);

const SaveAsTestCaseDialog = loadable(() => import("../components/modals/saveAsTestCaseDialog"), {
    fallback: <LoaderSpinner show />,
});

const PasteRecordsDialog = loadable(() => import("../components/modals/TestingDataRecords/PasteRecordsDialog"), {
    fallback: <LoaderSpinner show />,
});

const contentGetter = (props: WindowContentProps<WindowKind>) => {
    switch (props.data.kind) {
        case WindowKind.addFragment:
            return <AddProcessDialog {...props} isFragment />;
        case WindowKind.addProcess:
            return <AddProcessDialog {...props} />;
        case WindowKind.saveProcess:
            return <SaveProcessDialog {...props} />;
        case WindowKind.deployProcess:
            return <DeployProcessDialog {...props} />;
        case WindowKind.deployWithParameters:
            return <DeployWithParametersDialog {...props} />;
        case WindowKind.calculateCounts:
            return <CountsDialog {...props} />;
        case WindowKind.generateTestData:
            return <GenerateTestDataDialog {...props} />;
        case WindowKind.generateDataAndTest:
            return <GenerateDataAndTestDialog {...props} />;
        case WindowKind.compareVersions:
            return <CompareVersionsDialog {...props} />;
        case WindowKind.adhocTesting:
            return <AdhocTestingDialog {...props} />;
        case WindowKind.confirm:
            return <GenericConfirmDialog {...props} />;
        case WindowKind.inform:
            return <GenericInfoDialog {...props} />;
        case WindowKind.editNode:
            return <NodeDetails {...props} />;
        case WindowKind.viewNode:
            return <NodeDetails {...props} readOnly />;
        case WindowKind.editDescription:
            return <DescriptionDialog {...props} editMode />;
        case WindowKind.viewDescription:
            return <DescriptionDialog {...props} />;
        case WindowKind.survey:
            return <FrameDialog {...props} />;
        case WindowKind.remote:
            return <RemoteModuleDialog {...props} />;
        case WindowKind.withChildren:
            return <DialogWithChildren {...props} />;
        case WindowKind.scenarioDetails:
            return <ScenarioDetailsDialog {...props} />;
        case WindowKind.addComment:
            return <AddCommentDialog {...props} />;
        case WindowKind.modifyActivityComment:
            return <ModifyActivityCommentDialog {...props} />;
        case WindowKind.addAttachment:
            return <AddAttachmentDialog {...props} />;
        case WindowKind.editProperties:
            return <PropertiesDialog {...props} />;
        case WindowKind.aiAssistant:
            return <AiAssistantModal {...props} />;
        case WindowKind.enterpriseFeatureInfo:
            return <EnterpriseFeatureInfo {...props} />;
        case WindowKind.saveAsTestCase:
            return <SaveAsTestCaseDialog {...props} />;
        case WindowKind.editCepMeasures:
            return <MeasuresDialogContent {...props} />;
        case WindowKind.pasteRecords:
            return <PasteRecordsDialog {...props} />;
        default:
            return (
                <WindowContent {...props}>
                    <Debug data={props.data} />
                </WindowContent>
            );
    }
};

export const ContentGetter: React.FC<WindowContentProps<WindowKind>> = (props) => (
    <NuThemeProvider>
        <ErrorBoundary fallback={<DialogErrorFallbackComponent {...props} />}>{contentGetter(props)}</ErrorBoundary>
    </NuThemeProvider>
);
