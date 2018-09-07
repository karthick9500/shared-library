def call(config){

println config.'services-list'
def services_arr = config.'services-list'
services_arr.each { animalName, index ->
    println "${animalName}"
}



}
