/*
 1. Set Whitehat trigger
 2. Setup pipeline parameters
 3. discard old builds setup
 */

import groovy.json.JsonSlurperClassic
def call(config){
	try {

		echo '[INFO] Initializing Pipeline....'

		echo '[INFO] Sucessfully configured the parameters'

	} catch (e) {
			println "[ERROR] : Error configuring the parameters for the job: " + e
			throw e
		}
}
//Adds two Maps and returns the finalMap
def additionJoin(firstMap,secondMap)
{
	def resultMap = [:];
	resultMap.putAll( firstMap );
	resultMap.putAll( secondMap );
	resultMap.each { key, value ->
		if( firstMap[key] && secondMap[key] )
		{
			resultMap[key] = firstMap[key] + secondMap[key]
		}
	}

	return resultMap;
}
