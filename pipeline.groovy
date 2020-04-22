tag='latest'
agentIpList=''

/* Used for pulling the image. The withRegistry sets the context to retrieve only the docker image, not using the entire
link . That is why we need to perform the pull only using the image name
Yet, when running the container, we need to start it with the complete image name. Therefore we need two variables here
*/

tagged_image=docker.image('darksunset/sampledocker:'+tag)
image=docker.image('darksunset/sampledocker:'+tag)

/* We will hold the ip's of the JMeter Agent Containers in a list so we can forward it to the JMeter Master when starting the test
The handleList is used for storing the container handles of the JMeter Agents so we can perform shutdown of Agents
when finishing the test, or cleaning up when something goes wrong */

cIpList = []
cHandleList = []

//Use label to run pipeline only on docker labeled nodes. Set timeout to 60 minutes
timeout(240) {
    node('docker') {
        cleanWs deleteDirs: true, patterns: [[pattern: '*', type: 'INCLUDE']]
        stage('checkout') {
            checkout([$class: 'GitSCM', branches: [[name: "${BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'darksunset', url: 'https://github.com/darksunset/jmeter-docker-jenkins-pipeline.git']]])
        }
        // Change into jmeter subfolder, so we do not mount the entire eoc, but only the performance tests
        dir('jmeter') {
            try {

                docker.withRegistry('https://registry.hub.docker.com') {
                    tagged_image.pull()
                    stage('start_jmeter_agents') {
                        // Start 3 JMeter Agents and retrieve their IP and the container handle. Mount current folder into the container
                        for (i = 0; i < 3; i++) {
                            agent = image.run('-e SLEEP=1 -e JMETER_MODE=AGENT -v $WORKSPACE:/home/jmeter/tests', '')
                            agent_ip = sh(script: "docker inspect -f {{.NetworkSettings.IPAddress}} ${agent.id}", returnStdout: true).trim()
                            cIpList.add(agent_ip)
                            cHandleList.add(agent)
                        }
                        // Store the formatted list of JMeter Agent Ips in a String
                        agentIpList = cIpList.join(",")
                    }

                    stage('run_test') {
                        url = ${URL}.toString()
                        propertiesMap = [
                                'threads': "${THREADS}",
                                'rampUp': "${RAMPUP}",
                                'loopCount': "${LOOPCOUNT}",
                                'url': url
                        ]
                        performTest('dummy_test.jmx',"${STAGE_NAME}",setPlanProperties(propertiesMap))
                    }

                    stage('cleanup') {
                        // Handle shutdown of previous started JMeter Agents
                        cleanup(cHandleList)
                        cleanWs deleteDirs: true, patterns: [[pattern: '*', type: 'INCLUDE']]
                    }
                }
            }
            catch (Exception e) {
                // Handle shutdown of previous started JMeter Agents
                cleanup(cHandleList)
                cleanWs deleteDirs: true, patterns: [[pattern: '*', type: 'INCLUDE']]
            }
        }
    }
}

// Method for cleaning started JMeter Agents
def cleanup(containerHandleList) {
    for (i =0; i < containerHandleList.size(); i++) {
        containerHandleList[i].stop()
    }
}

def performTest(testplan,report,propertiesList) {
    image.inside('-e JMETER_MODE=MASTER -v $WORKSPACE:/home/jmeter/tests') {
        sh "jmeter -n -t /home/jmeter/tests/jmeter/testplans/$testplan -l $WORKSPACE/jmeter/${report}.jtl -e -o $WORKSPACE/jmeter/$report -Jsummariser.interval=5 -R$agentIpList $propertiesList"
    }
    publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: true, reportDir: ''+report, reportFiles: 'index.html', reportName: 'HTML Report '+report, reportTitles: ''])
}

def setPlanProperties(propertiesMap) {
    // Retrieve properties defined in the properties map for each plan, and create a string of properties
    // to be passed to JMeter
    propertiesList="-G"+propertiesMap.collect { k,v -> "$k=$v" }.join(' -G')
    println("property list "+propertiesList)
    return propertiesList
}

def configureCheckList(report) {
    constraintList = []
    // Get constraints from JSON File by looking after the key name = test name (given as variable)
    readConstraints = data.find { it['name'] == report }?.get("constraints")
    println("constraints determined dynamically are:"+readConstraints)
    readConstraints.absolute.each {
        constraintList.add(absolute(escalationLevel: 'WARNING', meteredValue: 'LINE90', operator: 'NOT_GREATER', relatedPerfReport: report + '.jtl', success: false, testCaseBlock: testCase(it.name), value: it.threshold))
    }
    println("my final constraint list: "+constraintList)
    return constraintList
}
