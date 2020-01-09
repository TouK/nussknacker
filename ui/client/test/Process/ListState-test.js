import React from 'react'

import {BACKEND_STATIC_URL} from "../../config"
import Enzyme, {mount} from 'enzyme'
import Adapter from 'enzyme-adapter-react-16'
import ListState from '../../components/Process/ListState'
import ProcessStateUtils from "../../common/ProcessStateUtils" //import redux-independent component
import urljoin from "url-join"

describe("ListState tests", () => {
  Enzyme.configure({ adapter: new Adapter() })

  it("should show defaults for no loaded state", () => {
    const process = {"processingType":"streaming","deployment":{"action":"DEPLOY"}}
    const listState = mount(<ListState process={process} />)
    expect(listState.find('img').prop('title')).toBe(ProcessStateUtils.getProcessTooltip(process))
    expect(listState.find('img').prop('src')).toBe(ProcessStateUtils.getProcessIcon(process))
  })

  it("should show defaults for loaded undefined state", () => {
    const process = {"processingType":"streaming","deployment":{"action":"DEPLOY"}}
    const listState = mount(<ListState process={process} state={{}} isStateLoaded={true} />)
    expect(listState.find('img').prop('title')).toBe(ProcessStateUtils.getStatusTooltip(ProcessStateUtils.STATUSES.ERROR))
    expect(listState.find('img').prop('src')).toBe(ProcessStateUtils.getStatusIcon(ProcessStateUtils.STATUSES.ERROR))
  })

  it("should show defaults for never deployed process", () => {
    const process = {"processingType":"streaming","deployment": null}
    const listState = mount(<ListState process={process} isStateLoaded={true} />)
    expect(listState.find('img').prop('title')).toBe(ProcessStateUtils.getProcessTooltip(process))
    expect(listState.find('img').prop('src')).toBe(ProcessStateUtils.getProcessIcon(process))
  })

  it("should show defaults for loaded canceled state", () => {
    const process = {"processingType":"streaming", "deployment": {"action":"CANCELED"}}
    const listState = mount(<ListState process={process} state={null} isStateLoaded={true} />)
    expect(listState.find('img').prop('title')).toBe(ProcessStateUtils.getStateTooltip(null))
    expect(listState.find('img').prop('src')).toBe(ProcessStateUtils.getStateIcon(null))
  })

  it("should show defaults loaded from state, cdn icon url", () => {
    const process = {"processingType":"streaming","deployment": {"action":"DEPLOY"}}
    const state = {"status":"RUNNING", "icon": "http://my-ftp.com/icons/test.svg", "tooltip": "Some tooltip"}
    const listState = mount(<ListState process={process} state={state} isStateLoaded={true} />)
    expect(listState.find('img').prop('title')).toBe(state.tooltip)
    expect(listState.find('img').prop('src')).toBe(state.icon)
  })

  it("should show defaults loaded from state, local icon url", () => {
    const process = {"processingType":"streaming","deployment": {"action":"DEPLOY"}}
    const state = {"status":"RUNNING", "icon": "/icons/test.svg", "tooltip": "Some tooltip"}
    const listState = mount(<ListState process={process} state={state} isStateLoaded={true} />)
    expect(listState.find('img').prop('title')).toBe(state.tooltip)
    expect(listState.find('img').prop('src')).toBe(urljoin(BACKEND_STATIC_URL, state.icon))
  })

  it("should show defaults for state without icon and tooltip and RUNNING STATUS", () => {
    const process = {"processingType":"streaming","deployment": {"action":"DEPLOY"}}
    const state = {"status":"RUNNING"}
    const listState = mount(<ListState process={process} state={state} isStateLoaded={true} />)
    expect(listState.find('img').prop('title')).toBe(ProcessStateUtils.getStatusTooltip(ProcessStateUtils.STATUSES.RUNNING))
    expect(listState.find('img').prop('src')).toBe(ProcessStateUtils.getStatusIcon(ProcessStateUtils.STATUSES.RUNNING))
  })

  it("should show defaults for state without icon and tooltip and unknown status", () => {
    const process = {"processingType":"streaming"}
    const state = {"status":"SOME_STATUS"}
    const listState = mount(<ListState process={process} state={state} isStateLoaded={true} />)
    expect(listState.find('img').prop('title')).toBe(ProcessStateUtils.getStatusTooltip(ProcessStateUtils.STATUSES.UNKNOWN))
    expect(listState.find('img').prop('src')).toBe(ProcessStateUtils.getStatusIcon(ProcessStateUtils.STATUSES.UNKNOWN))
  })
})
