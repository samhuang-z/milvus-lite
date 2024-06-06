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
        DOCKER_BUILDKIT = 1
        // PROJECT_NAME = 'milvus'
        // SEMVER = "${BRANCH_NAME.contains('/') ? BRANCH_NAME.substring(BRANCH_NAME.lastIndexOf('/') + 1) : BRANCH_NAME}"
        // ARTIFACTS = "${env.WORKSPACE}/_artifacts"
        // CI_DOCKER_CREDENTIAL_ID = 'harbor-milvus-io-registry'
        // MILVUS_HELM_NAMESPACE = 'milvus-ci'
        // DISABLE_KIND = true
        // HUB = 'harbor.milvus.io/milvus'
        // JENKINS_BUILD_ID = "${env.BUILD_ID}"
        // CI_MODE = 'pr'
        // SHOW_MILVUS_CONFIGMAP = true
    }

    stages {
        stage('Build') {
            steps {
                container('main') {
                    script {
                        sh '''

                        MIRROR_URL="https://docker-nexus-ci.zilliz.cc" ./ci/set_docker_mirror.sh
                        '''
                        sh '''
                         docker run --net=host  \
                            -e CONAN_USER_HOME=/root/  -v \$PWD:/root/milvus-lite -v /root/.conan:/root/.conan -w /root/milvus-lite  \
                            milvusdb/milvus-env:ubuntu20.04-20240520-d27db99 bash ci/entrypoint.sh
                         '''
                    }
                }
            }
        }
        stage('arhive Artifacts ') {
            steps {
                container('main') {
                        archiveArtifacts artifacts: 'python/dist/*.whl',
                               allowEmptyArchive: true,
                               fingerprint: true,
                               onlyIfSuccessful: true
                }
            }
        }
        stage('install wheel') {
            steps {

                container('pytest') {
                        sh '''
                        pip install ./python/dist/*.whl
                        '''
                }
            }
        }
        stage('Test') {
            steps {
                container('pytest') {

                            sh '''
                              bash ci/test.sh
                      '''
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
