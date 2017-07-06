import React from 'react';
import { shallow, mount, render } from 'enzyme';
import { ProcessHistory_ } from '../components/ProcessHistory'; //import redux-independent component

describe("ProcessHistory suite", () => {
  it("should mark latest history entry as current and other as past", () => {
    //given
    const processHistory = [processEntry(3), processEntry(2), processEntry(1)]
    //when
    const mountedProcessHistory = mount(<ProcessHistory_ processHistory={processHistory}/>)
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
      processId: "proc1",
      processName: "proc1",
      processVersionId: processVersionId,
      createDate: "2016-10-10T12:39:44.092",
      user: "TouK",
      deployments: []
    }
  }

});
