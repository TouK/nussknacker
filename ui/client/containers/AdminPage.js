import React from 'react'
import {Tab, TabList, TabPanel, Tabs} from 'react-tabs'
import * as  queryString from 'query-string'
import 'react-tabs/style/react-tabs.css'
import SearchComponents from "./admin/SearchComponents"
import UnusedComponents from "./admin/UnusedComponents"
import Services from "./admin/Services"
import CustomProcesses from "./admin/CustomProcesses"
import {withRouter} from 'react-router-dom'

class AdminPage extends React.Component {
  tabs = [
    {key: SearchComponents.key, title: SearchComponents.title, component: <SearchComponents/>},
    {key: UnusedComponents.key, title: UnusedComponents.title, component: <UnusedComponents/>},
    {key: Services.key, title: Services.title, component: <Services/>},
    {key: CustomProcesses.key, title: CustomProcesses.title, component: <CustomProcesses/>}
  ]

  constructor(props) {
    super(props)

    const query = queryString.parse(window.location.search, {parseNumbers: true})

    this.state = {
      selectedTab: query.tab || 0
    }
  }

  onTabChange = (index) => {
    const params = {tab: index}
    this.props.history.replace({search: queryString.stringify(params)})
    this.setState(params)
  }

  render() {
    return (
      <div className="Page">
        <Tabs defaultIndex={this.state.selectedTab} onSelect={this.onTabChange} >
          <TabList>
          {
            this.tabs.map(tab => {
              return (
                <Tab key={tab.key}>{tab.title}</Tab>
              )
            })
          }
          </TabList>
          {
            this.tabs.map( tab => {
              return (
                <TabPanel key={tab.key}>{tab.component}</TabPanel>
              )
            })
          }
        </Tabs>
      </div>
    )
  }
}

AdminPage.path = "/admin"
AdminPage.header = "Admin"

export default withRouter(AdminPage)