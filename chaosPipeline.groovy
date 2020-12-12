pipeline {
    agent none
    environment {
        RUN_NUM = ''
        ATTACK_ID1 = ''
        GREMLIN_API_KEY = credentials('gremlin-api-key')
    }
    parameters {
        string(name: 'SCENARIO1_ID', defaultValue: '509b83a7-a56f-4866-9b83-a7a56f1866cb', description: 'Scenario ID of the first Scenario')
    }
    stages {
        stage('Initiate Environment') {
            steps{
                echo "First spin up your chaos environment"
            }
        }

        stage('Install App into Environment') {
            steps{
                echo "Then install your app into said environment"
            }
        }

        stage('Load Test & 100MS of Latency') {
            agent any
            steps {
                script{ ATTACK_ID1 = sh (script: "curl -X POST 'https://api.gremlin.com/v1/kubernetes/attacks/new' \
                                                -H 'Content-Type: application/json;charset=utf-8' \
                                                -H 'Authorization: Key ${GREMLIN_API_KEY}' \
                                                -d '{\"targetDefinition\":{\"strategy\":{\"labels\":{},\"k8sObjects\":[{\"clusterId\":\"ts-demo\",\"uid\":\"bf9ca96b-a462-4282-8678-70e6c9ddd0d8\",\"namespace\":\"default\",\"name\":\"frontend\"}],\"percentage\":100}},\"impactDefinition\":{\"cliArgs\":[\"latency\",\"-l\",\"300\",\"-m\",\"100\",\"-h\",\"^api.gremlin.com\",\"-p\",\"^53\"]}}'",
                                            returnStdout: true).trim()
                
                    echo "See your attack at https://app.gremlin.com/attacks/kubernetes/$ATTACK_ID1"
                }

                script{
                    sh '/usr/bin/python3 -m bzt.cli /home/ubuntu/quick_test.yml -report'
                }

                script {
                    sh "curl -i -X POST 'https://api.gremlin.com/v1/kubernetes/attacks/$ATTACK_ID1/halt' \
                            -H 'Content-Type: application/json;charset=utf-8' \
                            -H 'Authorization: Key ${GREMLIN_API_KEY}' \
                            -d '{\"reason\": \"Automatic halt\",\"reference\":\"Jenkins\"}'"
                }
            }
        }

        stage('Load Test & Blackhole Ad Service') {
            agent any
            steps {
                script{ ATTACK_ID2 = sh (script: "curl -X POST 'https://api.gremlin.com/v1/kubernetes/attacks/new' \
                                                -H 'Content-Type: application/json;charset=utf-8' \
                                                -H 'Authorization: Key ${GREMLIN_API_KEY}' \
                                                -d '{\"targetDefinition\":{\"strategy\":{\"labels\":{},\"k8sObjects\":[{\"clusterId\":\"ts-demo\",\"uid\":\"003b7dfc-d4ec-4147-8617-6372b95a90ce\",\"namespace\":\"default\",\"name\":\"adservice\"}],\"percentage\":100}},\"impactDefinition\":{\"cliArgs\":[\"blackhole\",\"-l\",\"300\",\"-h\",\"^api.gremlin.com\",\"-p\",\"^53\"]}}'",
                                            returnStdout: true).trim()
                
                    echo "See your attack at https://app.gremlin.com/attacks/kubernetes/$ATTACK_ID2"
                }

                script{
                    sh '/usr/bin/python3 -m bzt.cli /home/ubuntu/quick_test.yml -report'
                }

                script {
                    sh "curl -i -X POST 'https://api.gremlin.com/v1/kubernetes/attacks/$ATTACK_ID2/halt' \
                            -H 'Content-Type: application/json;charset=utf-8' \
                            -H 'Authorization: Key ${GREMLIN_API_KEY}' \
                            -d '{\"reason\": \"Automatic halt\",\"reference\":\"Jenkins\"}'"
                }
            }
        }

        stage('Scenario with Status Check') {
            agent any
            steps {
                script {
                    RUN_NUM =
                        sh (script: "curl -X POST 'https://api.gremlin.com/v1/scenarios/${SCENARIO1_ID}/runs' \
                                -H 'Content-Type: application/json;charset=utf-8' -H 'Authorization: Key ${GREMLIN_API_KEY}' \
                                -d '{\"hypothesis\":\"No halt\"}'",
                        returnStdout: true).trim()

                    echo "See your attack at https://app.gremlin.com/scenarios/detail/${SCENARIO1_ID}/runs/$RUN_NUM"
                }

                script {
                    def response = sh(script: "curl -X GET 'https://api.gremlin.com/v1/scenarios/${SCENARIO1_ID}/runs/$RUN_NUM' \
                                    -H 'Authorization: Key ${GREMLIN_API_KEY}'", returnStdout: true).trim()
                    def jsonObj = readJSON text: response
                    def lifecycle = jsonObj.graph.nodes.concurrentNode.state.lifecycle

                    while(lifecycle == "NotStarted" || lifecycle == "Active") {
                        response = sh(script: "curl -X GET 'https://api.gremlin.com/v1/scenarios/${SCENARIO1_ID}/runs/$RUN_NUM' \
                                    -H 'Authorization: Key ${GREMLIN_API_KEY}'", returnStdout: true).trim()

                        jsonObj = readJSON text: response

                        lifecycle = jsonObj.graph.nodes.concurrentNode.state.lifecycle
                        sleep(5)
                    }

                    echo lifecycle

                    if(lifecycle == "HaltRequested" || lifecycle == "Failed"){
                        error "Status Check Halt"
                    }
                }
            }
        }
    }
}