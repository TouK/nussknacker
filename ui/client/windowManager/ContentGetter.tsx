import {WindowContentProps} from "@touk/window-manager"
import {DebugButtons} from "@touk/window-manager/esm/debug"
import React from "react"
import {AddProcessDialog} from "../components/AddProcessDialog"
import {EdgeDetails} from "../components/graph/node-modal/edge/EdgeDetails"
import {NodeDetails} from "../components/graph/node-modal/NodeDetails"
import {CountsDialog} from "../components/modals/CalculateCounts"
import {CompareVersionsDialog} from "../components/modals/CompareVersionsDialog"
import {CustomActionDialog} from "../components/modals/CustomActionDialog"
import {DeployProcessDialog} from "../components/modals/DeployProcessDialog"
import GenerateTestDataDialog from "../components/modals/GenerateTestDataDialog"
import {GenericConfirmDialog} from "../components/modals/GenericConfirmDialog"
import {SaveProcessDialog} from "../components/modals/SaveProcessDialog"
import {Debug} from "../containers/Debug"
import {WindowContent} from "./WindowContent"
import {WindowKind} from "./WindowKind"

export const contentGetter: React.FC<WindowContentProps<WindowKind>> = (props) => {
  switch (props.data.kind) {
    case WindowKind.addSubProcess:
      return <AddProcessDialog {...props} isSubprocess/>
    case WindowKind.addProcess:
      return <AddProcessDialog {...props}/>
    case WindowKind.saveProcess:
      return <SaveProcessDialog {...props}/>
    case WindowKind.deployProcess:
      return <DeployProcessDialog {...props}/>
    case WindowKind.calculateCounts:
      return <CountsDialog {...props}/>
    case WindowKind.generateTestData:
      return <GenerateTestDataDialog {...props}/>
    case WindowKind.compareVersions:
      return <CompareVersionsDialog {...props}/>

    case WindowKind.customAction:
      return <CustomActionDialog {...props}/>
    case WindowKind.confirm:
      return <GenericConfirmDialog {...props}/>
    case WindowKind.editNode:
      return <NodeDetails {...props}/>
    case WindowKind.viewNode:
      return <NodeDetails {...props} readOnly/>
    case WindowKind.editEdge:
      return <EdgeDetails {...props}/>
    default:
      return (
        <WindowContent {...props}>
          <Debug data={props.data}/>
          <DebugButtons currentId={props.data.id}/>
        </WindowContent>
      )
  }
}
