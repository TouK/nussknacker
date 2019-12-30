import React from 'react'

import Enzyme, {mount} from 'enzyme'
import Adapter from 'enzyme-adapter-react-16'
import {ListStateComponent} from '../../components/Process/ListState'
import ProcessStateUtils from "../../common/ProcessStateUtils" //import redux-independent component

const settings = {
 "icons": {
    "streaming": {
      "RUNNING": "My own svg icon for RUNNING status",
      "CANCELED": "My own svg icon for CANCELED status"
    }
  },
  "tooltips": {
    "streaming": {
      "RUNNING": "My own tooltip for RUNNING status",
      "CANCELED": "My own tooltip for CANCELED status"
    }
  }
}

const noSettings = {
  "icons": {
    "streaming": null
  },
  "tooltips": {
    "streaming": null
  }
}

describe("ListState tests", () => {
  Enzyme.configure({ adapter: new Adapter() })

  const loadingText = " Loading current state of the process..."
  const contains = (str, con) => str.substring(con) !== -1

  it("should show defaults for no loaded state and no settings configuration", () => {
    const process = {"processingType":"streaming","deployment":{"action":"DEPLOY"}}
    const listState = mount(<ListStateComponent process={process} processStatesSettings={noSettings} />)
    expect(listState.find('div').prop('title')).toBe(ProcessStateUtils.getProcessTooltip(process) + loadingText)
    expect(contains(listState.find('div').text(), ProcessStateUtils.getProcessTooltip(process))).toBe(true)
  })

  it("should show icon and tooltip from settings for not loaded stage", () => {
    const process = {"processingType":"streaming","deployment": {"action":"CANCEL"}}
    const listState = mount(<ListStateComponent process={process} processStatesSettings={settings} />)
    expect(listState.find('div').prop('title')).toBe(_.get(_.get(settings['tooltips'], process['processingType']), "CANCELED")  + loadingText)
    expect(contains(listState.find('div').text(), _.get(_.get(settings['icons'], process['processingType']), "CANCELED")  + loadingText)).toBe(true)
  })

  it("should show defaults for loaded undefined state and no settings configuration", () => {
    const process = {"processingType":"streaming","deployment":{"action":"DEPLOY"}}
    const listState = mount(<ListStateComponent process={process} state={{}} isStateLoaded={true} processStatesSettings={noSettings} />)
    expect(listState.find('div').prop('title')).toBe(ProcessStateUtils.getStatusTooltip(ProcessStateUtils.STATUSES.FAILED))
    expect(contains(listState.find('div').text(), ProcessStateUtils.getStatusIcon(ProcessStateUtils.STATUSES.FAILED))).toBe(true)
  })

  it("should show defaults for never deployed process", () => {
    const process = {"processingType":"streaming","deployment": null}
    const listState = mount(<ListStateComponent process={process} isStateLoaded={true} processStatesSettings={noSettings} />)
    expect(listState.find('div').prop('title')).toBe(ProcessStateUtils.getProcessTooltip(process))
    expect(contains(listState.find('div').text(), ProcessStateUtils.getProcessIcon(process))).toBe(true)
  })

  it("should show defaults for loaded canceled state and no settings configuration", () => {
    const process = {"processingType":"streaming", "deployment": {"action":"CANCELED"}}
    const listState = mount(<ListStateComponent process={process} state={null} isStateLoaded={true} processStatesSettings={noSettings} />)
    expect(listState.find('div').prop('title')).toBe(ProcessStateUtils.getStateTooltip( null))
    expect(contains(listState.find('div').text(), ProcessStateUtils.getStateIcon(null))).toBe(true)
  })

  it("should show defaults for loaded running state and no settings", () => {
    const process = {"processingType":"streaming","deployment": {"action":"DEPLOY"}}
    const state = {"status":"RUNNING"}
    const listState = mount(<ListStateComponent process={process} state={state} isStateLoaded={true} processStatesSettings={noSettings} />)
    expect(listState.find('div').prop('title')).toBe(ProcessStateUtils.getStateTooltip( state))
    expect(contains(listState.find('div').text(), ProcessStateUtils.getStateIcon(state))).toBe(true)
  })

  it("should show icon and tooltip from settings", () => {
    const process = {"processingType":"streaming","deployment": {"action":"DEPLOY"}}
    const state = {"status":"RUNNING"}
    const listState = mount(<ListStateComponent process={process} state={state} isStateLoaded={true} processStatesSettings={settings} />)
    expect(listState.find('div').prop('title')).toBe(_.get(_.get(settings['tooltips'], process['processingType']), state['status']))
    expect(contains(listState.find('div').text(), _.get(_.get(settings['icons'], process['processingType']), state['status']))).toBe(true)
  })
})
