// Sentinel CI on Jenkins.
//
// The repository's primary CI is GitHub Actions (.github/workflows/ci.yml), because it runs
// on every push with no infrastructure and reports a badge on the README. This Jenkinsfile
// exists because the enterprises this project is aimed at - banks and insurers - run Jenkins,
// and the same pipeline should be expressible on their stack.
//
// Requires two tools configured in Manage Jenkins > Tools, named exactly:
//   jdk21     an Eclipse Temurin 21 installation
//   maven-3.9 an Apache Maven 3.9.x installation
// Rename them here if your controller uses different tool names.

pipeline {
    agent any

    tools {
        jdk 'jdk21'
        maven 'maven-3.9'
    }

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    environment {
        // Keep the local repository inside the workspace so parallel builds on a shared
        // agent cannot corrupt each other's ~/.m2.
        MAVEN_OPTS = '-Dmaven.repo.local=.m2/repository -Djava.awt.headless=true'
    }

    stages {
        stage('Build and test') {
            steps {
                sh 'mvn -B clean verify'
            }
        }

        stage('Package') {
            // Only produce jars for a build that is actually a candidate for deployment.
            when { branch 'main' }
            steps {
                sh 'mvn -B -DskipTests package'
            }
        }
    }

    post {
        always {
            junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
        }
        success {
            archiveArtifacts artifacts: '**/target/*.jar',
                             fingerprint: true,
                             allowEmptyArchive: true
        }
        cleanup {
            cleanWs()
        }
    }
}
