import React from 'react'

import Enzyme, {mount} from 'enzyme'
import Adapter from 'enzyme-adapter-react-16'
import {ProcessHistoryComponent} from '../components/ProcessHistory' //import redux-independent component

describe("ProcessHistory suite", () => {
  it("should mark latest history entry as current and other as past", () => {
    Enzyme.configure({ adapter: new Adapter() })
    //given
    const processHistory = [processEntry(3), processEntry(2), processEntry(1)]
    //when
    const mountedProcessHistory = mount(<ProcessHistoryComponent history={processHistory} t={(key, def) => def}/>)
    //then
    const currentProcessHistoryEntry = mountedProcessHistory.find('.current')
    const pastHistoryEntries = mountedProcessHistory.find('.past')
    expect(currentProcessHistoryEntry.length).toBe(1)
    expect(pastHistoryEntries.length).toBe(2)
    expect(contains(currentProcessHistoryEntry.text(), "v3")).toBe(true)
    expect(contains(pastHistoryEntries.at(0).text(), "v2")).toBe(true)
    expect(contains(pastHistoryEntries.at(1).text(), "v1")).toBe(true)
  })

  //for some reason es6 'String.includes' does not work in tests...
  const contains = (str, con) => {
    return str.substring(con) !== -1
  }

  const processEntry = (processVersionId) => {
    return {
      processVersionId: processVersionId,
      createDate: "2016-10-10T12:39:44.092",
      user: "TouK",
      actions: []
    }
  }

})
