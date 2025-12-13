package com.example.v3

import java.util.UUID

// This file contains all the Bluetooth identifiers needed to communicate with the micro:bit.
// These are like phone numbers that help us find and talk to specific features on the micro:bit.


// The address of the micro:bit device we want to connect to.
// Every Bluetooth device has a unique address like this.
// If you have a different micro:bit you need to change this to match yours.
const val MICROBIT_ADDRESS = "C1:E8:3B:B1:F1:9B"


// These UUIDs identify the UART service on the micro:bit.
// UART is like a text messaging service that lets us send and receive messages.

// This identifies the main UART service on the micro:bit.
val UART_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")

// TX characteristic is where we WRITE data TO send it to the micro:bit.
// Think of it as the outbox for messages going to the micro:bit.
val UART_TX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

// RX characteristic is where we READ data FROM the micro:bit.
// Think of it as the inbox for messages coming from the micro:bit.
val UART_RX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

// This descriptor is used to turn on notifications.
// Notifications let the micro:bit send us data whenever it wants
// instead of us having to keep asking for it.
val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

