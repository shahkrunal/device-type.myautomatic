/**
 *  MyAutomaticServiceMgr
 *
 *  Copyright 2015 Yves Racine
 *  linkedIn profile: ca.linkedin.com/pub/yves-racine-m-sc-a/0/406/4b/
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
 
import java.text.SimpleDateFormat
import groovy.json.JsonSlurper
import net.sf.json.JSONObject
 
definition(
	name: "MyAutomaticServiceMgr",
	namespace: "yracine",
	author: "Yves Racine",
	description: "This smartapp is the Service Manager for My Automatic Device: it instantiates the Automatic device(s) for each vehicle and polls them on a regular basis",
	category: "My Apps",
	iconUrl: "https://www.automatic.com/_assets/images/favicons/favicon-32x32-3df4de42.png",
	iconX2Url: "https://www.automatic.com/_assets/images/favicons/favicon-96x96-06fd8c85.png",
	iconX3Url: "https://www.automatic.com/_assets/images/favicons/favicon-96x96-06fd8c85.png"
)

preferences {
	page(name: "about", title: "About", nextPage: "auth")
	page(name: "auth", title: "Automatic", content:"authPage", nextPage:"deviceList")
	page(name: "deviceList", title: "Automatic device(s)", content:"AutomaticDeviceList",nextPage:"otherSettings")
	page(name: "otherSettings", title: "Other Settings", content:"otherSettings", install:true)
}

mappings {

	path("/auth") {
		action: [
		  GET: "auth"
		]
	}

	path("/swapToken") {
		action: [
			GET: "swapToken"
		]
	}
	path("/procEvent") {
		action: [
			POST: "procEvent"
		]            
	}
}


def auth() {
	redirect location: oauthInitUrl()
}


def about() {
 	dynamicPage(name: "about", install: false, uninstall: true) {
 		section("About") {	
			paragraph "MyAutomaticServiceMgr, the smartapp that connects your Automatic connected vehicle(s) to SmartThings via cloud-to-cloud integration" +
				" and polls your Automatic device's events on a regular interval"
			paragraph "Version 0.8.4\n\n" +
			"If you like this app, please support the developer via PayPal:\n\nyracine@yahoo.com\n\n" +
			"Copyright©2015 Yves Racine"
			href url:"http://github.com/yracine", style:"embedded", required:false, title:"More information...", 
			description: "http://github.com/yracine"
		} 
	}        
}

def otherSettings() {
	dynamicPage(name: "otherSettings", title: "Other Settings", install: true, uninstall: false) {
		section("Polling at which interval in minutes (range=[5..59],default=30 min.)?") {
			input "givenInterval", "number", title:"Interval", required: false
		}
		section("Notifications") {
			input "sendPushMessage", "enum", title: "Send a push notification?", metadata: [values: ["Yes", "No"]], required:
				false
			input "phoneNumber", "phone", title: "Send a text message?", required: false
		}
		section([mobileOnly:true]) {
			label title: "Assign a name for this SmartApp", required: false
		}
	}
}


def authPage() {
	log.debug "authPage()"

	if (!atomicState.accessToken) {
		log.debug "about to create access token"
		createAccessToken()
		atomicState.accessToken = state.accessToken
	}


	def description = "Required"
	def uninstallAllowed = false

	if (atomicState.authToken) {

		// TODO: Check if it's valid
		if (true) {
			description = "You are connected."
			uninstallAllowed = true
			state.oauthTokenProvided=true
		} else {
			description = "Required" // Worth differentiating here vs. not having atomicState.authToken? 
		}
	}

	def redirectUrl = buildRedirectUrl("auth")
	log.debug "authPage>redirectUrl=${redirectUrl},atomicState.authToken=${atomicState.authToken},state.oauthTokenProvided=${state?.oauthTokenProvided}"


	// get rid of next button until the user is actually auth'd

	if (!state.oauthTokenProvided) {
		description = "Required" // Worth differentiating here vs. not having atomicState.authToken? 

		return dynamicPage(name: "auth", title: "Login", nextPage:null, uninstall:uninstallAllowed,submitOnChange: true) {
			section(){
				paragraph "Tap below to log in to the Automatic portal and authorize SmartThings access. Be sure to scroll down on page 2 and press the 'Allow' button."
				href url:redirectUrl, style:"embedded", required:true, title:"My Automatic", description:description
			}
		}

	} else {

		return dynamicPage(name: "auth", title: "Log In", nextPage:"deviceList", uninstall:uninstallAllowed,submitOnChange: true) {
			section(){
				paragraph "Tap Next to continue to setup your Automatic connected vehicle(s)"
				href url:redirectUrl, style:"embedded", state:"complete", title:"My Automatic", description:description
			}
		}

	}


}

def AutomaticDeviceList() {
	log.debug "AutomaticDeviceList()"

	def automaticDevices = getAutomaticDevices()

	log.debug "device list: $automaticDevices"

	def p = dynamicPage(name: "deviceList", title: "Select Your vehicle(s)",nextPage:"otherSettings") {
		section(""){
			paragraph "Tap below to see the list of Automatic connected vehicles available under your Automatic account and select the ones you want to connect to SmartThings."
			input(name: "AutomaticDevices", title:"", type: "enum", required:true, multiple:true, description: "Tap to choose", metadata:[values:automaticDevices])
		}
	}

	log.debug "list p: $p"
	return p
}



def getAutomaticDevices() {
	def AUTOMATIC_SUCCESS=200
    
	log.debug "getting Automatic devices list"
	def deviceListParams = [
		uri: "${get_URI_ROOT()}/vehicle/",
		headers: ["Authorization": "Bearer ${atomicState.authToken}"],
		Accept: "application/json",
		charset: "UTF-8"
	]

	log.debug "_______AUTH______ ${atomicState.authToken}"
	log.debug "device list params: $deviceListParams"

	def devices = [:]
	try {
		httpGet(deviceListParams) { resp ->

			if (resp.status == AUTOMATIC_SUCCESS) {
				log.debug "getAutomaticDevices>resp data = ${resp.data}" 
				def jsonMap =resp.data
				jsonMap.results.each {
					def vehicleId = it.id
					def make = it.make
					def model = it.model
					def submodel = it.submodel
					def year = it.year
					def color = it.color
					def displayName = it.display_name
                    
					log.debug "getAutomaticDevices>found vehicleId=${vehicleId},name=${displayName},make=${make},model=${model}"

					def dni = [ app.id, displayName, vehicleId].join('.')
					devices[dni] = displayName
					log.debug "getAutomaticDevices>vehicleId=${vehicleId}"
				} /* end each vehicle */                        
			} else {
				state?.msg= "trying to get list of Automatic connected vehicles, http error status: ${resp.status}"
				log.error state.msg        
				runIn(30, "sendMsgWithDelay")
			}
        
		}        
	} catch (java.net.UnknownHostException e) {
		state?.msg= "trying to get list of Automatic Devices, Unknown host - check the URL " + deviceListParams.uri
		log.error state.msg        
		runIn(30, "sendMsgWithDelay")
		        
	} catch (java.net.NoRouteToHostException t) {
		state?.msg= "trying to get list of Automatic Devices, No route to host - check the URL " + deviceListParams.uri
		log.error state.msg        
		runIn(30, "sendMsgWithDelay")
	} catch (e) {
		state?.msg= "exception $e while getting list of Automatic Devices" 
		log.error state.msg        
		runIn(30, "sendMsgWithDelay")
    }
    

	log.debug "devices: $devices"

	return devices
}



def setParentAuthTokens(auth_data) {
/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
	sendPush("setParentAuthTokens>begin auth_data: $auth_data")
*/

	atomicState.refreshToken = auth_data?.refresh_token
	atomicState.authToken = auth_data?.access_token
	atomicState.expiresIn=auth_data?.expires_in
	atomicState.tokenType = auth_data?.token_type
	atomicState.authexptime= auth_data?.authexptime
	refreshAllChildAuthTokens()
/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
	sendPush("setParentAuthTokens>New atomicState: $atomicState")
*/
}

def refreshAllChildAuthTokens() {
/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
	sendPush("refreshAllChildAuthTokens>begin updating children with $atomicState")
*/

	def children= getChildDevices()
/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
	sendPush("refreshAllChildAuthtokens> refreshing ${children.size()} thermostats")
*/

	children.each { 
/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
		sendPush("refreshAllChildAuthTokens>begin updating $it.deviceNetworkId with $atomicState")
*/
		it.refreshChildTokens(atomicState) 
	}
}

def refreshThisChildAuthTokens(child) {
/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
	sendPush("refreshThisChildAuthTokens>begin child id: ${child.device.deviceNetworkId}, updating it with ${atomicState}")
*/
	child.refreshChildTokens(atomicState)

/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
	sendPush("refreshThisChildAuthTokens>end")
*/
}



def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	unschedule()    
	initialize()

}


private def delete_child_devices() {
	def delete
    
	// Delete any that are no longer in settings
	if (!AutomaticDevices) {
		log.debug "delete_child_devices>deleting all Automatic Devices"
		delete = getAllChildDevices()
	} else {
		delete = getChildDevices().findAll { !AutomaticDevices.contains(it.deviceNetworkId) }
		log.debug "delete_child_devices>deleting ${delete.size()} Automatic Devices"
	}

	try { 
		delete.each { deleteChildDevice(it.deviceNetworkId) }
	} catch (e) {
		log.debug "delete_child_devices>exception $e while deleting ${delete.size()} Automatic devices"
	}
}




private def create_child_devices() {

	def devices = AutomaticDevices.collect { dni ->

		def d = getChildDevice(dni)
		log.debug "create_child_devices>looping thru Automatic Devices, found id $dni"

		if (!d) {
			def automatic_info  = dni.tokenize('.')
			def vehicleId = automatic_info.last()
 			def displayName = automatic_info[1]
			def labelName = 'My Automatic ' + "${displayName}"
			log.debug "create_child_devices>about to create child device with id $dni, vehicleId = $vehicleId, displayName= $displayName"
			d = addChildDevice(getChildNamespace(), getChildName(), dni, null,
				[label: "${labelName}"]) 
			d.initialSetup( getSmartThingsClientId(), atomicState, vehicleId ) 	// initial setup of the Child Device
			log.debug "create_child_devices>created ${d.displayName} with id $dni"
            
		} else {
			log.debug "create_child_devices>found ${d.displayName} with id $dni already exists"
		}


	}

	log.debug "create_child_devices>created ${devices.size()} Automatic Devices"
}



def initialize() {
    
	log.debug "initialize"
	state?.exceptionCount=0
	state?.oauthTokenProvided=false
	state?.msg=null    
	delete_child_devices()	
	create_child_devices()
    
    
	Integer delay = givenInterval ?: 30 // By default, do it every 30 min.
	if ((delay < 5) || (delay>59)) {
		state?.msg= "MyAutomaticServiceMgr>scheduling interval not in range (${delay} min), exiting..."
		log.debug state.msg
		runIn(30, "sendMsgWithDelay")
 		return
	}
	schedule("0 0/${delay} * * * ?", takeAction)
}

def takeAction() {
	log.trace "takeAction>begin"
	def MAX_EXCEPTION_COUNT=5    
	def msg
    String exceptionCheck
    
	def devices = AutomaticDevices.collect { dni ->
    
		def d = getChildDevice(dni)
		log.debug "takeAction>looping thru Automatic Devices, found id $dni, about to poll"
		try {
			d.poll()
			exceptionCheck = d.currentVerboseTrace.toString()
			if (((exceptionCheck.contains("exception") || (exceptionCheck.contains("error"))) && 
				(!exceptionCheck.contains("Java.util.concurrent.TimeoutException")))) {  
				// check if there is any exception reported in the verboseTrace associated to the device (except the ones linked to rate limiting).
				state.exceptionCount=state.exceptionCount+1    
				log.error "found exception after polling, exceptionCount= ${state?.exceptionCount}: $exceptionCheck" 
			} else {             
				// reset exception counter            
				state?.exceptionCount=0   
			}                
		} catch (e) {
			state?.exceptionCount=state?.exceptionCount+1        
			log.error "MyAutomaticServiceMgr>exception $e while trying to poll the device $d, exceptionCount= ${state?.exceptionCount}" 
		}
		if ((state?.exceptionCount>=MAX_EXCEPTION_COUNT) || (exceptionCheck.contains("Unauthorized"))) {
			// need to re-authenticate again    
			atomicState.authToken= null                    
			state?.oauthTokenProvided = false
			msg = "MyAutomaticServiceMgr>too many exceptions/errors or unauthorized exception, $exceptionCheck (${state?.exceptionCount} errors), need to re-authenticate at Automatic..." 
			log.error msg
			send msg
			        
		}        
	}
	log.trace "takeAction>end"
}

def procEvent() {
	def vehicleId
	def eventType
	def eventFields

 	log.debug "procEvent>params =${params}"
 	log.debug "procEvent>request =${request}"

	try {
		eventFields = new JsonSlurper().parseText(request.params[0])   
		log.debug "procEvent>eventFields = $eventFields"
	} catch (e) {
		log.error("procEvent>jsonEventData not formatted (exception $e) correctly or empty, exiting")
		return
	}
    
	if (eventFields) {
		vehicleId=eventsFields.vehicle.id
		def vehicleObject = getChildDevices().find { AutomaticDevices.contains(vehicleId) }
		if (vehicleObject) {
			log.debug "procEvent>found vehicle=$vehicleObject.vehicleId"
			vehicleObject.generateVehicleRTEvents("", eventFields)
		} else {

			log.error "procEvent>vehicleId =$vehicleObject.vehicleId not found"
		}    
	}    
	log.debug "procEvent>end"
}

def oauthInitUrl() {
	log.debug "oauthInitUrl"
	def stcid = getSmartThingsClientId();

	atomicState.oauthInitState = UUID.randomUUID().toString()

	def oauthParams = [
		response_type: "code",
		scope: "scope:public scope:user:profile scope:location scope:vehicle:profile " +
        	"scope:vehicle:events scope:trip scope:behavior", // to be added: scope:current_location", for RT events 
		client_id: stcid,
		redirect_uri: buildRedirectUrl()
	]

	return "${get_URI_ACCOUNT_ROOT()}/oauth/authorize?" + toQueryString(oauthParams)
}


def buildRedirectUrl(action = "swapToken") {
	log.debug "buildRedirectUrl, redirectURL=" +
		serverUrl + "/api/token/${atomicState.accessToken}/smartapps/installations/${app.id}/${action}"
	if (action=="swapToken"){	
		state.msg="MyAutomaticServiceMgr>" + serverUrl + "/api/token/${atomicState.accessToken}/smartapps/installations/${app.id}/${action}"        
		runIn(30,"sendMsgWithDelay")
	}        
	return serverUrl + "/api/token/${atomicState.accessToken}/smartapps/installations/${app.id}/${action}"
}

def swapToken() {
	log.debug "swapping token: $params"
	debugEvent ("swapping token: $params", true)

	def code = params.code
	def oauthState = params.state

	def stcid = getSmartThingsClientId()

	def tokenParams = [
		grant_type: "authorization_code",
		code: params.code,
		client_id: stcid,
		client_secret: getSmartThingsPrivateKey(),        
		redirect_uri: buildRedirectUrl()
	]
	def tokenMethod = [
		uri:"${get_URI_ACCOUNT_ROOT()}/oauth/access_token",
		body: toQueryString(tokenParams)
	]

	log.debug "Swapping token $params"

	def jsonMap
	try {	
		httpPost(tokenMethod) { resp ->
			jsonMap = resp.data
		}
	} catch ( e) {
		
		log.error ("exception ${e}, error swapping token: $resp.status")		
	}
	log.debug "Swapped token for $jsonMap"
	debugEvent ("swapped token for $jsonMap", true)

	atomicState.refreshToken = jsonMap.refresh_token
	atomicState.authToken = jsonMap.access_token
	atomicState.expiresIn=jsonMap.expires_in
	atomicState.tokenType = jsonMap.token_type
	def authexptime = new Date((now() + (365 * 24 * 60 * 1000))).getTime() // the token is valid for a year
	atomicState.authexptime = authexptime


	def html = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=640">
<title>Withings Connection</title>
<style type="text/css">
	@font-face {
		font-family: 'Swiss 721 W01 Thin';
		src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot');
		src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot?#iefix') format('embedded-opentype'),
			 url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.woff') format('woff'),
			 url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.ttf') format('truetype'),
			 url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.svg#swis721_th_btthin') format('svg');
		font-weight: normal;
		font-style: normal;
	}
	@font-face {
		font-family: 'Swiss 721 W01 Light';
		src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot');
		src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot?#iefix') format('embedded-opentype'),
			 url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.woff') format('woff'),
			 url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.ttf') format('truetype'),
			 url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.svg#swis721_lt_btlight') format('svg');
		font-weight: normal;
		font-style: normal;
	}
	.container {
		width: 560px;
		padding: 40px;
		/*background: #eee;*/
		text-align: center;
	}
	img {
		vertical-align: middle;
	}
	img:nth-child(2) {
		margin: 0 30px;
	}
	p {
		font-size: 2.2em;
		font-family: 'Swiss 721 W01 Thin';
		text-align: center;
		color: #666666;
		padding: 0 40px;
		margin-bottom: 0;
	}
/*
	p:last-child {
		margin-top: 0px;
	}
*/
	span {
		font-family: 'Swiss 721 W01 Light';
	}
</style>
</head>
<body>
	<div class="container">
		<img src="https://www.automatic.com/_assets/images/favicons/favicon-96x96-06fd8c85.png" width="216" height="216" alt="neurio icon" />
		<img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/connected-device-icn%402x.png" alt="connected device icon" />
		<img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/st-logo%402x.png" alt="SmartThings logo" />
		<p>Your Automatic Account is now connected to SmartThings!</p>
		<p>Click 'Done' to finish setup.</p>
	</div>
</body>
</html>
"""

	render contentType: 'text/html', data: html
}

def getChildDeviceIdsString() {
	return AutomaticDevices.collect { it.split(/\./).last() }.join(',')
}

def toJson(Map m) {
	return new org.codehaus.groovy.grails.web.json.JSONObject(m).toString()
}

def toQueryString(Map m) {
	return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}


def debugEvent(message, displayEvent) {

	def results = [
		name: "appdebug",
		descriptionText: message,
		displayed: displayEvent
	]
	log.debug "Generating AppDebug Event: ${results}"
	sendEvent (results)
}

private def get_URI_ACCOUNT_ROOT() {
	return "https://accounts.automatic.com"
}

private def get_URI_ROOT() {
	return "https://api.automatic.com"
}

private void sendMsgWithDelay() {

	if (state?.msg) {
		send "MyAutomaticServiceMgr> ${state.msg}"
	}
}

private send(msg) {
	if (sendPushMessage != "No") {
		log.debug("sending push message")
		sendPush(msg)

	}

	if (phoneNumber) {
		log.debug("sending text message")
		sendSms(phoneNumber, msg)
	}

	log.debug msg
}

def getChildNamespace() { "yracine" }

def getChildName() { "My Automatic Device" }

def getServerUrl() { return "https://graph.api.smartthings.com" }

def getSmartThingsClientId() { "insert your Automatic public key here!" }

def getSmartThingsPrivateKey() { "Insert your Automatic private key here!" }
