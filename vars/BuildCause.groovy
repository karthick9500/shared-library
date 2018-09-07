def call(config){
  // check if the job was triggered by timer, declared in Jenkinsfile
      def startedByTimer = false
      try {
          def buildCauses = currentBuild.rawBuild.getCauses()
          for ( buildCause in buildCauses ) {
              if (buildCause != null) {
                  def causeDescription = buildCause.getShortDescription()
                  println "[INFO] : Cause of Build: ${causeDescription}"
                  if (causeDescription.contains("Started by timer")) {
                      startedByTimer = true
                  }
              }
          }
      } catch(theError) {
          println "[ERROR] : Error getting build cause"
          error("[ERROR] : Error determining build cause. WhiteHat scan not executed.")
      }

      return startedByTimer
}
