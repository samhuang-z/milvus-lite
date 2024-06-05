#!/usr/bin/env groovy

int total_timeout_minutes = 60 * 5
int e2e_timeout_seconds = 120 * 60
def imageTag = ''
int case_timeout_seconds = 20 * 60
def chart_version = '4.1.8'
pipeline {
    options {
        timestamps()
        timeout(time: total_timeout_minutes, unit: 'MINUTES')
        buildDiscarder logRotator(artifactDaysToKeepStr: '30')
        parallelsAlwaysFailFast()
        preserveStashes(buildCount: 5)
        disableConcurrentBuilds(abortPrevious: true)
        // skipDefaultCheckout()
    }
    agent {
            kubernetes {
                cloud '4am'
                inheritFrom 'milvus-e2e-4am'
                defaultContainer 'main'
                yamlFile 'ci/jenkins/pod/rte-build.yaml'
                customWorkspace '/home/jenkins/agent/workspace'
            }
    }
    environment {
        PROJECT_NAME = 'milvus'
        SEMVER = "${BRANCH_NAME.contains('/') ? BRANCH_NAME.substring(BRANCH_NAME.lastIndexOf('/') + 1) : BRANCH_NAME}"
        DOCKER_BUILDKIT = 1
        ARTIFACTS = "${env.WORKSPACE}/_artifacts"
        CI_DOCKER_CREDENTIAL_ID = 'harbor-milvus-io-registry'
        MILVUS_HELM_NAMESPACE = 'milvus-ci'
        DISABLE_KIND = true
        HUB = 'harbor.milvus.io/milvus'
        JENKINS_BUILD_ID = "${env.BUILD_ID}"
        CI_MODE = 'pr'
        SHOW_MILVUS_CONFIGMAP = true
    }

    stages {
        stage('Build') {
            steps {
                container('main') {
                    script {
                        // checkout scm
                        // git submodule update --init --recursive

                            // chown -R jenkins:jenkins /home/jenkins/agent/workspace/thirdparty/
                            // git config --global --add safe.directory /home/jenkins/agent/workspace
                            // git config --global --add safe.directory /home/jenkins/agent/workspace/thirdparty
                            // git config --global --add safe.directory /home/jenkins/agent/workspace/thirdparty/milvus
                        sh '''


                        MIRROR_URL="https://docker-nexus-ci.zilliz.cc" ./ci/set_docker_mirror.sh
                        '''
                        // sh 'printenv'
                        // def date = sh(returnStdout: true, script: 'date +%Y%m%d').trim()
                        sh '''
                         docker run --net=host -e CONAN_USER_HOME=/root/  -v \$PWD:/root/milvus-lite -v /root/.conan:/root/.conan -w /root/milvus-lite  milvusdb/milvus-env:ubuntu20.04-20240520-d27db99  bash -c "git config --global --add safe.directory /root/milvus-lite/thirdparty/milvus;cd python; python3 setup.py bdist_wheel"
                         '''
                        // dir('scripts') {
                        //     sh 'chmod +x ./build.sh'
                        //     sh './build.sh main /root'
                        // }

                        // sh 'sleep 600'
                        archiveArtifacts artifacts: 'python/dist/*.whl',
                               allowEmptyArchive: true,
                               fingerprint: true,
                               onlyIfSuccessful: true
                    }
                }
            }
        }
        stage('Test') {
            steps {
                container('pytest') {
                    script {
                      sh '''
                        
                        sleep 600
                        pip install ./python/dist/*.whl

                        export PIP_TRUSTED_HOST="nexus-nexus-repository-manager.nexus"
                        export PIP_INDEX_URL="http://nexus-nexus-repository-manager.nexus:8081/repository/pypi-all/simple"
                        export PIP_INDEX="http://nexus-nexus-repository-manager.nexus:8081/repository/pypi-all/pypi"
                        export PIP_FIND_LINKS="http://nexus-nexus-repository-manager.nexus:8081/repository/pypi-all/pypi"
                        python3 -m pip install --no-cache-dir -r requirements.txt --timeout 300 --retries 6
                      '''
                      
                    }
                }
            }
        }
    }
    post {
        unsuccessful {
                container('jnlp') {
                    dir('tests/scripts') {
                        script {
                            def authorEmail = sh(returnStdout: true, script: './get_author_email.sh ')
                            emailext subject: '$DEFAULT_SUBJECT',
                            body: '$DEFAULT_CONTENT',
                            recipientProviders: [developers(), culprits()],
                            replyTo: '$DEFAULT_REPLYTO',
                            to: "${authorEmail},devops@zilliz.com"
                        }
                    }
                }
        }
    }
}
