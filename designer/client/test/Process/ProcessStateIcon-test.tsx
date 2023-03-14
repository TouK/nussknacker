import React from "react"
import ProcessStateIcon from "../../components/Process/ProcessStateIcon"
import {describe, expect, it, jest} from "@jest/globals"
import {render, waitFor} from "@testing-library/react"

const processState = {
  allowedActions: ["DEPLOY"],
  icon: "/states/stopping-success.svg",
  status: {type: "StoppedStateStatus", name: "CANCELED"},
  tooltip: "The scenario has been successfully cancelled."
}

const noDataProcessState = {
  allowedActions: ["DEPLOY"],
  status: {type: "StoppedStateStatus", name: "CANCELED"}
}

global.fetch = jest.fn(url => Promise.resolve({
  text: () => Promise.resolve(`<span data-testid="svg">${url}</span>`)
})) as any

describe("ProcessStateIcon tests", () => {
  it("should show defaults for missing process.state and stateProcess", async () => {
    const process = {processingType: "streaming"}
    const {getByTestId, container} = render(
      <ProcessStateIcon process={process as any}/>
    )
    expect(fetch).toHaveBeenLastCalledWith("/be-static/assets/states/status-unknown.svg")
    await waitFor(() => getByTestId("svg"))
    expect(container).toMatchSnapshot()
  })

  it("should show defaults for loaded process.state without data", async () => {
    const process = {processingType: "streaming", state: noDataProcessState}
    const {getByTestId, container} = render(
      <ProcessStateIcon process={process as any}/>
    )
    await waitFor(() => getByTestId("svg"))
    expect(fetch).not.toHaveBeenCalled() //cached
    expect(container).toMatchSnapshot()
  })

  it("should show data from loaded process.state", async () => {
    const process = {processingType: "streaming", state: processState}
    const {getByTestId, container} = render(
      <ProcessStateIcon process={process as any}/>
    )
    expect(fetch).toHaveBeenLastCalledWith("/be-static/states/stopping-success.svg")
    await waitFor(() => getByTestId("svg"))
    expect(container).toMatchSnapshot()
  })

  it("should show defaults if loadedProcess is null", async () => {
    const process = {processingType: "streaming", state: processState}
    const {getByTestId, container} = render(
      <ProcessStateIcon process={process as any} isStateLoaded/>
    )
    await waitFor(() => getByTestId("svg"))
    expect(fetch).not.toHaveBeenCalled() //cached
    expect(container).toMatchSnapshot()
  })

  it("should show defaults if loadedProcess is empty", async () => {
    const process = {processingType: "streaming", state: processState}
    const {getByTestId, container} = render(
      <ProcessStateIcon process={process as any} processState={noDataProcessState as any}
                        isStateLoaded={true}/>
    )
    await waitFor(() => getByTestId("svg"))
    expect(fetch).not.toHaveBeenCalled() //cached
    expect(container).toMatchSnapshot()
  })

  it("should show loadedProcess data", async () => {
    const {getByTestId, container} = render(
      <ProcessStateIcon process={noDataProcessState as any} processState={processState as any}
                        isStateLoaded={true}/>
    )
    await waitFor(() => getByTestId("svg"))
    expect(fetch).not.toHaveBeenCalled() //cached
    expect(container).toMatchSnapshot()
  })
})
