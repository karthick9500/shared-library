@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7.1')
import groovyx.net.http.RESTClient


def call(config){

if (config == null || config == '')
        error ("JSON Config Object cannot be null. Please make sure pipeline.json is being read correctly.")

echo "[INFO] Building Maven Project"
echo "[INFO] Product name :"+ config.'product-name'

//sh "export JAVA_HOME=/usr/lib/jvm/java-8-openjdk"
def MVN_PROJECT_ROOT = "${env.WORKSPACE}/DTI-SSH-Parent"
def MVN_GOALS = 'clean install -D maven.test.skip=true'

  try {
        sh "${env.M2_HOME}/bin/mvn -f " + MVN_PROJECT_ROOT + "/pom.xml " + MVN_GOALS

        }catch(err){

          println '[ERROR] Could not publish junit reports'
      } 

}








def testing(config) {
    if (config == null || config == '')
        error ("JSON Config Object cannot be null. Please make sure pipeline.json is being read correctly.")

    echo "[INFO] Building Maven Project"
    echo "[INFO] Product name :"+ config.'product-name'

    // declaring the defaults
    def sonarqubeUrl = LoadPipelineProps("sonarqube-url")
    def INHOUSE_SNAPSHOT = 'inhouse_snapshot'
    def INHOUSE_RELEASE = 'inhouse_release'
    def ARTIFACTORY_URL = ""
    def DEFAULT_MAVEN_HOME = tool 'maven-3.3.9'
    def DEFAULT_SONAR_EXCLUSIONS = [
                "**/target/**,**/target/*",
                "**/ui.resources/node_modules/**",
                "**/ui.resources/node_modules/*",
                "**/ui.resources/bower_components/**",
                "**/ui.resources/bower_components/*",
                "**/*.jpg,**/*.svg,**/vendor.bundle.js"
        ]
    def DEFAULT_SONAR_COVERAGE_EXCLUSIONS = ['**/it.tests/**']

    // validate and override defaults if required
    def productName = config.'product-name'
      if (productName == null || productName == '')
        throw new Exception("Product Name is not configured in the JSON file.")

    def buildGroup = config.stages.'maven-build'
    if (buildGroup == null)
      throw new Exception("Maven build parameters are not configured in the JSON file.")

    // parse pom.xml and get project info
    pomString = readFile("${env.WORKSPACE}/pom.xml")
    def parsedPom = new XmlParser().parseText(pomString)
    def pomVersion = parsedPom.version[0].text()
    def versionList = pomVersion.split('-')
    def appVersion = versionList[0]
    def snapshot = pomVersion.findAll("SNAPSHOT")
    def repo = snapshot.size > 0 ? INHOUSE_SNAPSHOT : INHOUSE_RELEASE
    def groupId = parsedPom.groupId[0].text()
    def artifactId = parsedPom.artifactId[0].text()
    def htmlReports = buildGroup.'html-reports'

    def cobertura = buildGroup.'cobertura'
    def coberturaReportPath = buildGroup.'cobertura-report-path'?: '**/target/site/cobertura/coverage.xml, **/cobertura.xml'
    def clover = buildGroup.'clover'
    def cloverReportPath = buildGroup.'clover-report-path'?: 'target/site/clover/clover.xml'
    def jslcovReportPath = buildGroup.'js-lcov-report-path'?: ''
    def tslcovReportPath = buildGroup.'ts-lcov-report-path'?: ''
    def sonarCoverageExclusions = buildGroup.'sonar-coverage-exclusions'
    def whitehat = buildGroup.'whiteHat'


    def sonar = true   //default sonar is true if not specified
	  sonar = buildGroup.'sonar'
    def sonarExclusions = buildGroup.'sonar-exclusions'
    def appVersionPath="${ARTIFACTORY_URL}/${repo}/${groupId.replace(".", "/")}/${artifactId}/${pomVersion}/"

    def testResultFile = buildGroup.'test-result-xml'
    if (testResultFile == null || testResultFile == '')
        throw new Exception("Maven build test-result-xml parameters are not configured in the JSON file.")

    def goals = 'clean '
    def skipTestFlag = buildGroup.'skip-test-flag'

    if ( "${BRANCH_NAME}" == "master" || "${BRANCH_NAME}" == "develop" || "${BRANCH_NAME}".contains("release/")){
        goals = goals + 'install '
    } else {
        goals = goals + 'install '
    }

    def mvnSettings = buildGroup.'maven-settings'

    if (mvnSettings != null && mvnSettings != '')
      goals = '-s ' + mvnSettings + ' ' + goals

      if (skipTestFlag){
         echo '[INFO] Skipping unit tests'
         goals = goals + ' -Dmaven.test.skip=true '
      }

      if (cobertura){
        echo "[INFO] Invoking cobertura goal"
        goals = goals + 'cobertura:cobertura -Dcobertura.report.format=xml '
      }

      if (clover){
        echo "[INFO] Invoking clover goal"
        goals = goals + 'clover:instrument-test clover:check clover:aggregate clover:clover -U '
      }


      try {
          sh "${DEFAULT_MAVEN_HOME}/bin/mvn "  + goals
        } finally {
            if (!skipTestFlag){
              try{
              	JUnit(testResultFile)
              } catch(err){
                println '[ERROR] Could not publish junit reports'
              }	
            }
        }


        // get artifactory URL and rename build job
        def artifactoryURL = getArtifactoryURL()
        println '[INFO] Artifactory URL ' + artifactoryURL


        // code coverage and publishing reports
        if (cobertura && !skipTestFlag)
          CoberturaTest(coberturaReportPath)

        if(clover && cloverReportPath){
          println '[INFO] Publishing Clover Report'
          def cloverReportFile = "${cloverReportPath}".split('/')[-1]
          def cloverReportDir = "${cloverReportPath}".replace('/'+"${cloverReportFile}","")

          step([
                $class: 'CloverPublisher',
                cloverReportDir: "${cloverReportDir}",
                cloverReportFileName: "${cloverReportFile}",
                healthyTarget: [methodCoverage: 70, conditionalCoverage: 80, statementCoverage: 80], // optional, default is: method=70, conditional=80, statement=80
                unhealthyTarget: [methodCoverage: 40, conditionalCoverage: 40, statementCoverage: 40], // optional, default is none
                failingTarget: [methodCoverage: 0, conditionalCoverage: 0, statementCoverage: 0]     // optional, default is none
          ])
        }

        // sonar execution
        if(sonar){
            def exclusions = (sonarExclusions)? DEFAULT_SONAR_EXCLUSIONS.plus(sonarExclusions) : DEFAULT_SONAR_EXCLUSIONS
            def scannerHome = tool name: 'SonarQube Runner', type: 'hudson.plugins.sonar.SonarRunnerInstallation'
            def coverageExclusions = (sonarCoverageExclusions)? DEFAULT_SONAR_COVERAGE_EXCLUSIONS.plus(sonarCoverageExclusions) : DEFAULT_SONAR_COVERAGE_EXCLUSIONS
              withSonarQubeEnv('Sonar') {
                sh """
                   ${scannerHome}/bin/sonar-runner -e \\
                  -Dsonar.projectKey=org.sonarqube:${productName} \\
                  -Dsonar.host.url=${sonarqubeUrl} \\
                  -Dsonar.projectName=${productName} \\
                  -Dsonar.projectVersion=${pomVersion} \\
                  -Dsonar.sources=. -Dsonar.exclusions='${(exclusions).join(', ')}' \\
                  -Dsonar.coverage.exclusions='${(coverageExclusions).join(', ')}' \\
                  -Dsonar.clover.reportPath=${cloverReportPath} \\
                  -Dsonar.javascript.lcov.reportPaths=${jslcovReportPath} \\
                  -Dsonar.typescript.lcov.reportPaths=${tslcovReportPath} \\
                  -Dsonar.cobertura.reportPath='${coberturaReportPath}' \\
                  -Dsonar.sourceEncoding=UTF-8 \\
									-Dsonar.java.binaries=.
                  """
              }
        }

        analysisId = sonarwebapi.getAnalysisId()
        echo '[INFO] AnalysisID retrived from .sonar/report-task.txt : ' + analysisId
        qualityGateMetrics = sonarwebapi.getQualityMetrics(analysisId)
        echo '[INFO] Properties to be added to artifact ' + qualityGateMetrics
        // tag artifact with sonar metrics
        artifactorytagging.tagQualityProps(artifactoryURL, "sonar", qualityGateMetrics)

}

def getArtifactoryURL(){
  def INHOUSE_SNAPSHOT = 'inhouse_snapshot'
  def INHOUSE_RELEASE = 'inhouse_release'
  def ARTIFACTORY_URL = "https://artifactory-fof.appl.kp.org/artifactory"
  def artifactURL
  def pom = readMavenPom file: 'ui.apps/pom.xml'
  def parentPom = pom.getParent()
	def artifactId = pom.getArtifactId()
  def version = pom.getVersion()

  if(version == null || version == ""){
		version = parentPom.getVersion()
	}

  def snapshot = version.findAll('SNAPSHOT')
  def repo = snapshot.size > 0 ? INHOUSE_SNAPSHOT : INHOUSE_RELEASE
	def groupId = pom.getGroupId()
	if(groupId == null || groupId == ""){
		groupId = parentPom.getGroupId()
	}

  if(groupId == null || version == null || artifactId == null){
			error("[ERROR] Could not retrive artifact details from project pom.xml")
	}

    echo "[INFO] Project Details : "
    echo "Artifact ID " + artifactId
    echo "Group ID " + groupId
    echo "Version " + version

    def appVersionPath="${ARTIFACTORY_URL}/${repo}/${groupId.replace(".", "/")}/${artifactId}/${version}/"

    if(repo == INHOUSE_SNAPSHOT){
        echo "[INFO] Artifact Type " + repo
        def client = new RESTClient(appVersionPath)
        def response = client.get(path: "maven-metadata.xml")
        def buildnum = null
        def timestamp = null

        buildnum = response.data.versioning.snapshot.buildNumber
        timestamp = response.data.versioning.snapshot.timestamp

        artifactURL = appVersionPath + artifactId + "-" + version.substring(0, version.length() - 9) +
								"-" + timestamp + "-" + buildnum + '.zip'

        def buildName = artifactId + '-' + version + '-' + buildnum
        client=null; response=null; buildnum=null; timestamp=null
        script {
          currentBuild.displayName = "${buildName}"
        }

    } else {
        echo "[INFO] Artifact Type " + repo
        artifactURL = appVersionPath + artifactId + '-' + version + '.zip'
        def buildName = artifactId + '-' + version
        script {
          currentBuild.displayName = "${buildName}"
        }
    }
    pom = null; parentPom=null; artifactId=null; version=null
    return artifactURL
}
