import React from "react"
import Enzyme, {mount} from "enzyme"
import Adapter from "enzyme-adapter-react-16"
import ListState from "../../components/Process/State/StateIcon"
import ProcessStateUtils from "../../components/Process/State/ProcessStateUtils"
import {unknownTooltip} from "../../components/Process/ProcessMessages"
import {absoluteBePath} from "../../common/UrlUtils"

//TODO: In future we shoulde convert it to ts - now, we have some problems with this..

const processState = {
  allowedActions: ["DEPLOY"],
  attributes: null,
  deploymentId: null,
  errorMessage: null,
  icon: "/states/stopping-success.svg",
  startTime: null,
  status: {type: "StoppedStateStatus", name: "CANCELED"},
  tooltip: "The process has been successfully cancelled."
}

const noDataProcessState = {
  allowedActions: ["DEPLOY"],
  attributes: null,
  deploymentId: null,
  errorMessage: null,
  icon: null,
  startTime: null,
  status: {type: "StoppedStateStatus", name: "CANCELED"}
}

describe("ListState tests", () => {
  Enzyme.configure({ adapter: new Adapter() })

  it("should show defaults for missing process.state and stateProcess", () => {
    const process = {processingType:"streaming"}
    const listState = mount(<ListState process={process} />)
    expect(listState.find('img').prop('title')).toBe(unknownTooltip())
    expect(listState.find('img').prop('src')).toBe(absoluteBePath(ProcessStateUtils.UNKNOWN_ICON))
  })

  it("should show defaults for loaded process.state without data", () => {
    const process = {processingType: "streaming", state: noDataProcessState}
    const listState = mount(<ListState process={process} />)
    expect(listState.find('img').prop('title')).toBe(unknownTooltip())
    expect(listState.find('img').prop('src')).toBe(absoluteBePath(ProcessStateUtils.UNKNOWN_ICON))
  })

  it("should show data from loaded process.state", () => {
    const process = {processingType: "streaming", state: processState}
    const listState = mount(<ListState process={process} />)
    expect(listState.find('img').prop('title')).toBe(processState.tooltip)
    expect(listState.find('img').prop('src')).toBe(absoluteBePath(processState.icon))
  })

  it("should show defaults if loadedProcess is null ", () => {
    const process = {processingType: "streaming", state: processState}
    const listState = mount(<ListState process={process} isStateLoaded={true} />)
    expect(listState.find('img').prop('title')).toBe(unknownTooltip())
    expect(listState.find('img').prop('src')).toBe(absoluteBePath(ProcessStateUtils.UNKNOWN_ICON))
  })

  it("should show defaults if loadedProcess is empty ", () => {
    const process = {processingType: "streaming", state: processState}
    const listState = mount(<ListState process={process} processState={noDataProcessState} isStateLoaded={true} />)
    expect(listState.find('img').prop('title')).toBe(unknownTooltip())
    expect(listState.find('img').prop('src')).toBe(absoluteBePath(ProcessStateUtils.UNKNOWN_ICON))
  })

  it("should show loadedProcess data ", () => {
    const listState = mount(<ListState process={noDataProcessState} processState={processState} isStateLoaded={true} />)
    expect(listState.find('img').prop('title')).toBe(processState.tooltip)
    expect(listState.find('img').prop('src')).toBe(absoluteBePath(processState.icon))
  })
})
