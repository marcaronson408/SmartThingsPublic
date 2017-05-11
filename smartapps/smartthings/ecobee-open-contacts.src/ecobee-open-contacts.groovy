/**
 *  ecobee Open Contacts
 *
 *  Copyright 2016 Sean Kendall Schneyer
 *
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
 *	1.0.0	-	Preparation for General Release
 *	1.0.1	-	Tweaked LOG and setup for consistency across all the Helper SmartApps
 *	1.0.2	-	Fixed 'clear()' and a typo or two
 *  1.0.3	-	Optimized prior fix
 *	1.0.4	- 	Added support for switches, configurable open/closed/on/off
 *
 */
 
 /**
  * TODO: Add support for more than on/off such as programs
  */
def getVersionNum() { return "1.0.4" }
private def getVersionLabel() { return "ecobee Open Contacts version ${getVersionNum()}" }



definition(
	name: "ecobee Open Contacts",
	namespace: "smartthings",
	author: "Sean Kendall Schneyer (smartthings at linuxbox dot org)",
	description: "Turn HVAC on/off based on status of contact sensors or switches",
	category: "Convenience",
	parent: "smartthings:Ecobee (Connect)",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Partner/ecobee.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Partner/ecobee@2x.png",
	singleInstance: false
)

preferences {
	page(name: "mainPage")
}

// Preferences Pages
def mainPage() {
	dynamicPage(name: "mainPage", title: "Setup Routines", uninstall: true, install: true) {
    	section(title: "Name for Contacts Handler") {
        	label title: "Name this Contacts Handler", required: true, defaultValue: "Open Contacts Handler"
        }
        
        section(title: "Select Thermostats") {
        	if(settings.tempDisable == true) paragraph "WARNING: Temporarily Disabled as requested. Turn back on to activate handler."
        	input ("myThermostats", "capability.Thermostat", title: "Pick Ecobee Thermostat(s)", required: true, multiple: true, submitOnChange: true)            
		}
	
		if (myThermostats?.size() > 0) {

			section(title: "Select Contact Sensors") {
				input(name: "contactSensors", title: "Contact Sensors: ", type: "capability.contactSensor", required: false, multiple: true, description: "")
                if (contactSensors) {
                	input(name: 'contactOpen', type: 'bool', title: 'Stop HVAC when contact(s) are open?', required: true, defaultValue: true)
                }
			}
            
            section(title: "Select Switches") {
            	input(name: "theSwitches", title: "Switches: ", type: "capability.switch", required: false, multiple: true, description: "")
                if (theSwitches) {
                	input(name: 'switchOn', type: 'bool', title: 'Stop HVAC when switch(es) are turned on?', required: true, defaultValue: true)
                }
        	}
            
            if ((contactSwitches != null) && (theSwitches != null)) {
				section(title: "Timers") {
					input(name: "offDelay", title: "Delay time (in minutes) before turning off HVAC or Sending Notification [Default=5]", type: "enum", required: true, metadata: [values: [0, 1, 2, 3, 4, 5, 10, 15, 30]], defaultValue: 5)
					input(name: "onDelay", title: "Delay time (in minutes) before turning HVAC back on  or Sending Notification [Default=0]", type: "enum", required: true, metadata: [values: [0, 1, 2, 3, 4, 5, 10, 15, 30]], defaultValue: "0")        	
	        	}
            
            	section(title: "Action Preferences") {
            		input(name: "whichAction", title: "Select which actions to take [Default=Notify Only]", type: "enum", required: true, metadata: [values: ["Notify Only", "HVAC Only", "Notify and HVAC"]], defaultValue: "Notify Only", submitOnChange: true)
					if (settings.whichAction != "HVAC Only") {
						input("recipients", "contact", title: "Send notifications to") {
							input "phone", "phone", title: "Warn with text message (optional)", description: "Phone Number", required: false
        				}                
                	}
            	}
            }
            
		} // End if (myThermostats?.size() > 0)

		section(title: "Temporarily Disable?") {
			input(name: "tempDisable", title: "Temporarily Disable Handler? ", type: "bool", required: false, description: "", submitOnChange: true)                
        }
        
        section (getVersionLabel())
    }
}

// Main functions
def installed() {
	LOG("installed() entered", 5)
	initialize()  
}

def updated() {
	LOG("updated() entered", 5)
	unsubscribe()
	unschedule()
	initialize()
}

def initialize() {
	LOG("initialize() entered")
	if(tempDisable == true) {
		LOG("Teporarily Disabled as per request.", 2, null, "warn")
		return true
	}
    
    // TODO: update based on real status?
    // if (allClosed()) {
    //	state.openedState = "closed"
    // } else {
    //	state.openedState = "opened"
    //}

	Boolean contactOffState = false
	if (contactSensors) {
    	if (contactOpen) {
    		subscribe(contactSensors, "contact.open", sensorOpened)
			subscribe(contactSensors, "contact.closed", sensorClosed)
            contactOffState = contactSensors.currentContact.contains('open')
       	} else {
        	subscribe(contactSensors, "contact.closed", sensorOpened)
			subscribe(contactSensors, "contact.open", sensorClosed)
            contactOffState = contactSensors.currentContact.contains('closed')
        }
    }
    
    Boolean switchOffState = false
    if (theSwitches) {
    	if (switchOn) {
        	subscribe(theSwitches, "switch.on", sensorOpened)
            subscribe(theSwitches, "switch.off", sensorClosed)
            switchOffState = theSwitches.currentSwitch.contains('on')
        } else {
        	subscribe(theSwitches, "switch.off", sensorOpened)
            subscribe(theSwitches, "switch.on", sensorClosed)
            switchOffState = theSwitches.currentSwitch.contains('off')
        }
    }
    
    atomicState.HVACOffState = (contactOffState || switchOffState)
    
    // TODO: Subscribe to the thermostat states to be notified when the HVAC is turned on or off outside of the SmartApp?
	
	LOG("initialize() exiting")
}

def HVACOff(evt) { sensorOpened(evt) }
def HVACOn(evt) { sensorClosed(evt) }

// "sensorOpened" called when state change should turn HVAC off - routine named preserved for backwards compatibility with prior implementations
def sensorOpened(evt) {
	// A sensor (door/window) was opened, or switch was turned on
	LOG("sensorOpened() entered with event ${evt.device} ${evt.name}: ${evt.value}", 3)
	
	def gotEvent = evt.value?.toLowerCase()
//	LOG("--- Event name received (in lowercase): ${gotEvent}", 5)
//    LOG("--- Event data received: ${evt.data}", 5)
//   LOG("--- Event descriptionText: ${evt.descriptionText}", 5)
//    LOG("--- Event device: ${evt.device}", 5)
//    LOG("--- Event deviceId: ${evt.deviceId}", 5)
    
    if(state.openedState == "closed_pending") {
    	// Just need to cancel the close
        state.openedState = "opened"
    	try {
        	unschedule(closedScheduledActions)
		} catch (Exception e) {
        	LOG("Failed to unschedule, possibly nothing scheduled. ${e}", 4)
        }
    } else if(state.openedState == "closed" || state.openedState == "closed_pending") {
    	state.openedState = "open_pending"
    	try {
        	unschedule(closedScheduledActions)
		} catch (Exception e) {
        	LOG("Failed to unschedule, possibly nothing scheduled. ${e}", 4)
        }
        
		int delay = settings.offDelay?.toInteger()
        LOG("The off delay is ${delay} from ${settings.offDelay}", 5)


		if(delay > 0) {
//        	LOG("Delay is greater than zero (0)", 5)
        	runIn(delay*60, openedScheduledActions)
        } else if (delay == 0) {
        	// turn on immediately
            turnoffHVAC()
        }  
	}
}

def openedScheduledActions() {
	LOG("openedScheduledActions entered", 5)
    turnoffHVAC()
}

def sensorClosed(evt) {
	// A sensor (door/window) was closed
    LOG("sensorClosed() entered with evt: ${evt}", 5)
    
    def gotEvent = evt.value?.toLowerCase()	
	LOG("Event name received (in lowercase): ${gotEvent}", 5)
    
    if ( allClosed() == true) {
    	if (state.openedState == "open_pending" ) {
        	// Cancel the open pending and just return to closed
        	state.openedState = "closed"
           	try {
				unschedule(openedScheduledActions)
			} catch (Exception e) {
    	    	LOG("Failed to unschedule, possibly nothing scheduled. ${e}", 4)
        	} 
        
        } else {
    		// Process based on timers
	        LOG("All Contact Sensors are now closed, initiating actions.", 5)		
        
        	state.openedState = "closed_pending"
	        // If all the windows are now closed, we don't want to continue with any scheduled actions
    	     
        	try {
				unschedule(openedScheduledActions)
			} catch (Exception e) {
    	    	LOG("Failed to unschedule, possibly nothing scheduled. ${e}", 4)
        	} 
         
	        int delay = settings.onDelay?.toInteger()
    	    LOG("The on delay is ${delay} from ${settings.onDelay}", 5)


			if(delay > 0) {
    	    	LOG("Delay is on greater than zero (0)", 5)
        		runIn(delay*60, closedScheduledActions)
	        } else if (delay == 0) {
    	    	// turn on immediately
                LOG("Delay is zero, turning on now...", 5)
        	    turnonHVAC()
	        }  
    	}
	} else {
    	LOG("Some Contact Sensors are still open, no action to perform yet...", 5)
    }
}

def closedScheduledActions() {
	LOG("closedScheduledActions entered", 5)
	turnonHVAC()
}

private turnoffHVAC() {
	// Save current states
    LOG("turnoffHVAC() called...", 5)
        
    def tmpThermSavedState = [:]
    settings.myThermostats.each() { therm ->
    	LOG("Got therm: ${therm}", 5)
    	tmpThermSavedState[therm.id] = therm.latestValue("thermostatMode")
        LOG("Updated state: ${therm.latestValue("thermostatMode")}", 5)
    }
    if (state.thermSavedState) state.thermSavedState = null
    state.thermSavedState = tmpThermSavedState
    LOG("Turning off HVACs per action.", 5)
    if( settings.whichAction.contains("HVAC") ) {
    	settings.myThermostats*.off()
	}
    
    if( settings.whichAction.contains("Notify") ) {
    	sendNotification("Door or Window left open for at least ${settings.offDelay} minutes")
    }
    
    state.openedState = "opened"
    LOG("turnoffHVAC() exiting...", 5)
}

private turnonHVAC() {
	// Restore previous state
	LOG("turnonHVAC() entered", 5)
    
    def action = settings.whichAction
    if( action.contains("HVAC") ) { LOG("whichAction contains HVAC", 5) } else { LOG("whichAction didn't contain HVAC?", 5) }
    if( action.contains("HVAC") ) {
	   	// Restore to previous state 
        LOG("Restoring to previous state", 5) 
        
        settings.myThermostats.each { therm ->
			LOG("Working on thermostat: ${therm}", 5)
            def thermId = therm.id
            def mode = state.thermSavedState[thermId] 
            
			LOG("Setting the thermostat ${thermId} mode to ${mode}", 4)
			therm.setThermostatMode(mode)
		} 
	}
    
	if( settings.whichAction.contains("Notify") ) {
    	sendNotification("All Door or Window contacts have now been closed")
    }    
    
    state.openedState = "closed"
    LOG("turnonHVAC() exited", 5)
}

private Boolean allClosed() {
	// Check if all Sensors are closed   
    // LOG("allClosed() entered", 5)
    def response = true
    
    if (contactSensors) {
    	settings.contactSensors.each() {
    		// LOG("Sensor ${it.displayName} state is ${it}", 5)
    		if (contactOpen && (it.latestValue("contact") == "open")) {
        		LOG("Sensor ${it.displayName} is open, Returning false", 5)
            	response = false
            } else if (!contactOpen && (it.latestValue("contact") == "closed")) {
                LOG("Sensor ${it.displayName} is closed, Returning false", 5)
                response = false
            }
        }
    }
    if (theSwitches) {
    	settings.theSwitches.each() {
    		// LOG("Switch ${it.displayName} state is ${it}", 5)
    		if (switchOn && (it.latestValue("switch") == "on")) {
        		LOG("Switch ${it.displayName} is on, Returning false", 5)
            	response = false
            } else if (!switchOn && (it.latestValue("switch") == "off")) {
                LOG("Switch ${it.displayName} is off, Returning false", 5)
                response = false
            }
        }
    }
    
    if (response) LOG("Returning true")
    return response
}


// Helper Functions
private def sendNotification(message) {	
    if (location.contactBookEnabled && recipients) {
        LOG("Contact Book enabled!", 5)
        sendNotificationToContacts(message, recipients)
    } else {
        LOG("Contact Book not enabled", 5)
        if (phone) {
            sendSms(phone, message)
        }
    }
}

private def LOG(message, level=3, child=null, logType="debug", event=true, displayEvent=true) {
	message = "${app.label} ${message}"
	parent.LOG(message, level, null, logType, event, displayEvent)
    log."${logType}" message
}
