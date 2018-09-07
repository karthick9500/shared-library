import groovy.json.JsonSlurperClassic
def call(propName){
  echo '[INFO] Loading Pipeline Properties'
  //load pipeline shared libs json
  def loadScript = libraryResource "pipeline_properties.json"
  writeFile file: "pipeline_properties.json", text: loadScript
  inputFile = readFile("${WORKSPACE}/pipeline_properties.json")
  pipelineProps = new JsonSlurperClassic().parseText(inputFile)
  if(pipelineProps == null)
        throw new Exception("AEM Environments object is null. Read file: pipeline_properties.json from shared libs.")

  def env = determineEnv()
  switch(propName){
    case 'artifactory-url':
      return pipelineProps.'artifactory-url'."${env}"
    case 'sonarqube-url':
      return pipelineProps.'sonarqube-url'."${env}"
    case 'rabbitmq-host':
      return pipelineProps.'rabbitmq-host'."${env}"
    default:
      return pipelineProps
  }
}

def determineEnv(){
  if ("${BUILD_URL}".contains("local")){
		return 'local'
	} else if ("${BUILD_URL}".contains("sandbox")){
		return 'sandbox'
	} else {
		return 'production'
	}
}
