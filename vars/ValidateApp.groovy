def call(appUrl,message) {

def app_webserv = appUrl
def app_message = message
sh """

                  if curl -s "${app_webserv}" | grep "${app_message}" \\
                  then \\
                  echo " the website is working fine" \\
                  else \\
                  echo "Error" \\
                  fi
                  """
      


}
