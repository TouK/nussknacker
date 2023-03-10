import React from "react"
import ProcessStateIcon from "../../components/Process/ProcessStateIcon"
import {describe, expect, it, jest} from "@jest/globals"
import {render, waitFor} from "@testing-library/react"

//TODO: In future we shoulde convert it to ts - now, we have some problems with this..

const processState = {
  allowedActions: ["DEPLOY"],
  attributes: null,
  deploymentId: null,
  errorMessage: null,
  icon: "/states/stopping-success.svg",
  startTime: null,
  status: {type: "StoppedStateStatus", name: "CANCELED"},
  tooltip: "The scenario has been successfully cancelled."
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

// @ts-ignore
global.fetch = jest.fn(async url => {
  return await ({
    async text() {
      return await (`<span data-testid="svg">${url}</span>`)
    }
  })
})

describe("ProcessStateIcon tests", () => {
  it("should show defaults for missing process.state and stateProcess", async () => {
    const process = {processingType: "streaming"}
    const {getByTestId, container} = render(
      <ProcessStateIcon process={process}/>
    )
    expect(fetch).toHaveBeenLastCalledWith("/be-static/assets/states/status-unknown.svg")
    await waitFor(() => getByTestId("svg"))
    expect(container).toMatchSnapshot()
  })

  it("should show defaults for loaded process.state without data", async () => {
    const process = {processingType: "streaming", state: noDataProcessState}
    const {getByTestId, container} = render(
      <ProcessStateIcon process={process}/>
    )
    await waitFor(() => getByTestId("svg"))
    expect(fetch).not.toHaveBeenCalled() //cached
    expect(container).toMatchSnapshot()
  })

  it("should show data from loaded process.state", async () => {
    const process = {processingType: "streaming", state: processState}
    const {getByTestId, container} = render(
      <ProcessStateIcon process={process}/>
    )
    expect(fetch).toHaveBeenLastCalledWith("/be-static/states/stopping-success.svg")
    await waitFor(() => getByTestId("svg"))
    expect(container).toMatchSnapshot()
  })

  it("should show defaults if loadedProcess is null", async () => {
    const process = {processingType: "streaming", state: processState}
    const {getByTestId, container} = render(
      <ProcessStateIcon process={process} isStateLoaded={true}/>
    )
    await waitFor(() => getByTestId("svg"))
    expect(fetch).not.toHaveBeenCalled() //cached
    expect(container).toMatchSnapshot()
  })

  it("should show defaults if loadedProcess is empty", async () => {
    const process = {processingType: "streaming", state: processState}
    const {getByTestId, container} = render(
      <ProcessStateIcon process={process} processState={noDataProcessState}
                        isStateLoaded={true}/>
    )
    await waitFor(() => getByTestId("svg"))
    expect(fetch).not.toHaveBeenCalled() //cached
    expect(container).toMatchSnapshot()
  })

  it("should show loadedProcess data", async () => {
    const {getByTestId, container} = render(
      <ProcessStateIcon process={noDataProcessState} processState={processState}
                        isStateLoaded={true}/>
    )
    await waitFor(() => getByTestId("svg"))
    expect(fetch).not.toHaveBeenCalled() //cached
    expect(container).toMatchSnapshot()
  })
})
