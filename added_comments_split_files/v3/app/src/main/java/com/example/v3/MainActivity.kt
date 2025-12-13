package com.example.v3

// This is the main screen of the Android app.
// It connects to the micro:bit via Bluetooth and runs three games:
// 1. Solo reaction time test
// 2. Two player battle mode
// 3. Number memory game
// It also saves scores to Firebase and shows history.

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query


@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    // SOLO MODE SCREEN VIEWS
    // These show connection status and reaction time for single player mode
    private lateinit var connectionStatusTextView: TextView
    private lateinit var reactionTimeTextView: TextView
    private lateinit var startButton: Button
    private lateinit var testScreen: View
    
    // BATTLE MODE SCREEN VIEWS
    // These are for two player mode where players take turns
    private lateinit var battleScreen: View
    private lateinit var battleStartButton: Button
    private lateinit var battleStatus: TextView
    private lateinit var player1Time: TextView
    private lateinit var player2Time: TextView
    private lateinit var winnerText: TextView
    private lateinit var turnIndicator: TextView
    
    // MEMORY GAME SCREEN VIEWS
    // These are for the number memory game
    private lateinit var memoryScreen: View
    private lateinit var memoryLevel: TextView
    private lateinit var memoryStatus: TextView
    private lateinit var memoryDisplay: TextView
    private lateinit var memoryInputLayout: TextInputLayout
    private lateinit var memoryInput: TextInputEditText
    private lateinit var memoryResult: TextView
    private lateinit var memoryHighScore: TextView
    private lateinit var memorySubmitButton: Button
    
    // HISTORY SCREEN VIEWS
    // These show past reaction time scores
    private lateinit var historyScreen: View
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyStatsText: TextView


    // BLUETOOTH SETUP
    // This gets the Bluetooth adapter from the phone
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    
    // The Bluetooth connection to the micro:bit
    private var bluetoothGatt: BluetoothGatt? = null
    
    // Prevents trying to connect multiple times at once
    private var isConnecting = false


    // FIREBASE
    // This connects to the cloud database where we save scores
    private val db = FirebaseFirestore.getInstance()
    

    // HISTORY DATA
    // This list holds all the reaction times loaded from Firebase
    private val historyList = mutableListOf<ReactionTimeEntry>()
    
    // This adapter connects the history list to the scrollable view
    private lateinit var historyAdapter: HistoryAdapter


    // GAME STATE
    // Tracks which game mode is currently active
    private var currentGameMode = GameMode.SOLO
    
    // Battle mode tracking
    private var battleState = BattleState.IDLE
    private var player1ReactionTime: Long? = null
    private var player2ReactionTime: Long? = null
    
    // Memory game tracking
    private var memoryState = MemoryState.IDLE
    private var currentMemoryLevel = 1
    private var currentNumbers = ""
    private var memoryBestScore = 0


    // BLUETOOTH CHARACTERISTICS
    // These are the data channels we use to send and receive messages
    private var uartTxCharacteristic: BluetoothGattCharacteristic? = null
    private var uartRxCharacteristic: BluetoothGattCharacteristic? = null


    // PERMISSION LAUNCHER
    // This handles asking the user for Bluetooth permissions
    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.entries.any { !it.value }) {
                // Some permissions were denied so Bluetooth wont work
            } else {
                // All permissions granted so start scanning for the micro:bit
                startBleScan()
            }
        }


    // BLUETOOTH SCAN CALLBACK
    // This gets called when we find a Bluetooth device while scanning
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            
            // Skip if we are already trying to connect
            if (isConnecting) return
            
            // Check if this device is our micro:bit by checking address or name
            if (result.device.address == MICROBIT_ADDRESS || 
                result.device.name?.contains("micro:bit") == true) {
                
                isConnecting = true
                stopBleScan()
                connectToDevice(result.device)
            }
        }
    }


    // BLUETOOTH GATT CALLBACK
    // This handles all Bluetooth events like connecting, disconnecting, and receiving data
    private val gattCallback = object : BluetoothGattCallback() {
        
        // Called when we connect or disconnect from the micro:bit
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d("MainActivity", "Connection state changed: status=$status, newState=$newState")
            
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    // We connected to the micro:bit
                    bluetoothGatt = gatt
                    Log.d("MainActivity", "Connected to: ${gatt?.device?.address}")
                    
                    // Update the screen to show we are connected
                    runOnUiThread {
                        connectionStatusTextView.text = "● Connected"
                        connectionStatusTextView.setTextColor(getColor(android.R.color.holo_green_light))
                    }
                    
                    // Now find out what services the micro:bit has
                    gatt?.discoverServices()
                }
                
                BluetoothGatt.STATE_DISCONNECTED -> {
                    // We lost connection to the micro:bit
                    bluetoothGatt = null
                    uartTxCharacteristic = null
                    uartRxCharacteristic = null
                    isConnecting = false
                    
                    // Update the screen to show we are disconnected
                    runOnUiThread {
                        connectionStatusTextView.text = "● Disconnected"
                        connectionStatusTextView.setTextColor(getColor(android.R.color.holo_red_light))
                        startButton.isEnabled = false
                        battleStartButton.isEnabled = false
                        memorySubmitButton.isEnabled = false
                    }
                }
            }
        }

        // Called after we discover what services the micro:bit has
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.d("MainActivity", "Services discovered, status: $status")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Try to find the UART service which we use for messaging
                val uartService = gatt?.getService(UART_SERVICE_UUID)
                
                if (uartService != null) {
                    Log.d("MainActivity", "UART service found!")
                    
                    // Get the TX and RX channels for sending and receiving
                    uartTxCharacteristic = uartService.getCharacteristic(UART_TX_CHAR_UUID)
                    uartRxCharacteristic = uartService.getCharacteristic(UART_RX_CHAR_UUID)

                    // Set up notifications so we can receive data from the micro:bit
                    uartRxCharacteristic?.let { rxChar ->
                        gatt.setCharacteristicNotification(rxChar, true)
                        
                        val descriptor = rxChar.getDescriptor(CCCD_UUID)
                        if (descriptor != null) {
                            val props = rxChar.properties
                            val useIndication = props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                            
                            val descriptorValue = if (useIndication) {
                                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                            } else {
                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            }
                            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gatt.writeDescriptor(descriptor, descriptorValue)
                            } else {
                                @Suppress("DEPRECATION")
                                descriptor.value = descriptorValue
                                @Suppress("DEPRECATION")
                                gatt.writeDescriptor(descriptor)
                            }
                        }
                    }

                    // Enable all the game buttons now that we are ready
                    runOnUiThread {
                        startButton.isEnabled = true
                        battleStartButton.isEnabled = true
                        memorySubmitButton.isEnabled = true
                        connectionStatusTextView.text = "● Ready"
                        connectionStatusTextView.setTextColor(getColor(android.R.color.holo_green_light))
                    }
                }
            }
        }

        // Called after we write a descriptor to enable notifications
        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Send a test message to make sure the connection works
                sendWarmupPing()
            }
        }

        // Called if writing data to the micro:bit fails
        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread {
                    reactionTimeTextView.text = "Write failed!"
                    startButton.isEnabled = true
                    battleStartButton.isEnabled = true
                    memorySubmitButton.isEnabled = true
                }
            }
        }

        // Called when we receive data from the micro:bit
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicChanged(gatt, characteristic)
            
            if (characteristic?.uuid == UART_RX_CHAR_UUID) {
                // Read the message as text
                @Suppress("DEPRECATION")
                val data = characteristic.value?.toString(Charsets.UTF_8)?.trim()
                Log.d("MainActivity", "Received: $data")

                data?.let {
                    when {
                        // RT:250 means reaction time of 250 milliseconds
                        it.startsWith("RT:") -> {
                            val reactionTime = it.removePrefix("RT:")
                            val reactionTimeMs = reactionTime.toLongOrNull()
                            
                            runOnUiThread { handleReactionTimeReceived(reactionTimeMs ?: 0L) }
                            
                            // Save to Firebase if playing solo mode
                            if (currentGameMode == GameMode.SOLO) {
                                reactionTimeMs?.let { time -> saveReactionTimeToFirebase(time) }
                            }
                        }
                        
                        // WAIT means the countdown is done and waiting for the flash
                        it == "WAIT" -> {
                            runOnUiThread {
                                when (currentGameMode) {
                                    GameMode.SOLO -> reactionTimeTextView.text = "Wait for flash..."
                                    GameMode.BATTLE -> turnIndicator.text = "Wait for flash..."
                                    else -> {}
                                }
                            }
                        }
                        
                        // EARLY means the user pressed the button before the flash
                        it == "EARLY" -> {
                            runOnUiThread { handleEarlyPress() }
                        }
                        
                        // MEM:472 means these are the memory game numbers
                        it.startsWith("MEM:") -> {
                            val numbers = it.removePrefix("MEM:").trim()
                            Log.d("MainActivity", "Memory numbers received: '$numbers' (length: ${numbers.length})")
                            runOnUiThread { handleMemoryNumbersReceived(numbers) }
                        }
                        
                        // MEMDONE means the micro:bit finished showing the numbers
                        it == "MEMDONE" -> {
                            runOnUiThread { handleMemoryDisplayDone() }
                        }
                        
                        // SHAKE means the user shook the micro:bit to restart
                        it == "SHAKE" -> {
                            Log.d("MainActivity", "Shake detected - restarting solo test")
                            runOnUiThread { handleShakeRestart() }
                        }
                        
                        // PONG means our test message was received
                        it == "PONG" -> {
                            Log.d("MainActivity", "Warmup complete")
                        }
                    }
                }
            }
        }
    }


    // ACTIVITY LIFECYCLE METHODS
    // These are called by Android at different points in the apps life

    // Called when the app first starts
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupNavigation()
        setupButtons()
        requestPermissions()
    }
    
    // Finds all the views in the layout and saves references to them
    private fun initViews() {
        // Solo mode views
        connectionStatusTextView = findViewById(R.id.connection_status_textview)
        reactionTimeTextView = findViewById(R.id.reaction_time_textview)
        startButton = findViewById(R.id.start_button)
        testScreen = findViewById(R.id.test_screen)
        
        // Battle mode views
        battleScreen = findViewById(R.id.battle_screen)
        battleStartButton = findViewById(R.id.battle_start_button)
        battleStatus = findViewById(R.id.battle_status)
        player1Time = findViewById(R.id.player1_time)
        player2Time = findViewById(R.id.player2_time)
        winnerText = findViewById(R.id.winner_text)
        turnIndicator = findViewById(R.id.turn_indicator)
        
        // Memory game views
        memoryScreen = findViewById(R.id.memory_screen)
        memoryLevel = findViewById(R.id.memory_level)
        memoryStatus = findViewById(R.id.memory_status)
        memoryDisplay = findViewById(R.id.memory_display)
        memoryInputLayout = findViewById(R.id.memory_input_layout)
        memoryInput = findViewById(R.id.memory_input)
        memoryResult = findViewById(R.id.memory_result)
        memoryHighScore = findViewById(R.id.memory_high_score)
        memorySubmitButton = findViewById(R.id.memory_submit_button)
        
        // History views
        historyScreen = findViewById(R.id.history_screen)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        historyRecyclerView = findViewById(R.id.history_recycler_view)
        historyStatsText = findViewById(R.id.history_stats)

        // Set up the history list
        historyAdapter = HistoryAdapter(historyList)
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        historyRecyclerView.adapter = historyAdapter
    }
    
    // Sets up the bottom tab bar to switch between screens
    private fun setupNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_test -> {
                    currentGameMode = GameMode.SOLO
                    showScreen(testScreen)
                    true
                }
                R.id.nav_battle -> {
                    currentGameMode = GameMode.BATTLE
                    showScreen(battleScreen)
                    resetBattle()
                    true
                }
                R.id.nav_memory -> {
                    currentGameMode = GameMode.MEMORY
                    showScreen(memoryScreen)
                    resetMemoryGame()
                    true
                }
                R.id.nav_history -> {
                    showScreen(historyScreen)
                    loadHistory()
                    true
                }
                else -> false
            }
        }
    }
    
    // Shows one screen and hides all the others
    private fun showScreen(screen: View) {
        testScreen.visibility = View.GONE
        battleScreen.visibility = View.GONE
        memoryScreen.visibility = View.GONE
        historyScreen.visibility = View.GONE
        screen.visibility = View.VISIBLE
    }
    
    // Sets up what happens when buttons are clicked
    private fun setupButtons() {
        startButton.isEnabled = false
        startButton.setOnClickListener { startReactionTest() }
        
        battleStartButton.isEnabled = false
        battleStartButton.setOnClickListener { handleBattleButtonClick() }
        
        memorySubmitButton.isEnabled = false
        memorySubmitButton.setOnClickListener { handleMemoryButtonClick() }
        
        // Submit answer when user presses Done on keyboard
        memoryInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (memoryState == MemoryState.INPUT) {
                    checkMemoryAnswer()
                }
                true
            } else false
        }
    }
    
    // Asks the user for Bluetooth permissions
    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 and newer needs these permissions
            requestMultiplePermissions.launch(arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ))
        } else {
            // Older Android needs these permissions
            requestMultiplePermissions.launch(arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            ))
        }
    }


    // REACTION TIME HANDLERS
    // These handle the reaction time results from the micro:bit
    
    // Called when we receive a reaction time from the micro:bit
    private fun handleReactionTimeReceived(reactionTimeMs: Long) {
        when (currentGameMode) {
            GameMode.SOLO -> {
                // Show the time and enable the button again
                reactionTimeTextView.text = "$reactionTimeMs ms"
                startButton.isEnabled = true
            }
            GameMode.BATTLE -> {
                when (battleState) {
                    BattleState.PLAYER1_WAITING -> {
                        // Player 1 finished their test
                        player1ReactionTime = reactionTimeMs
                        player1Time.text = reactionTimeMs.toString()
                        battleState = BattleState.PLAYER2_TURN
                        battleStatus.text = "Player 1: ${reactionTimeMs}ms - Player 2's turn!"
                        battleStartButton.text = "PLAYER 2 GO"
                        battleStartButton.isEnabled = true
                        turnIndicator.visibility = View.GONE
                    }
                    BattleState.PLAYER2_WAITING -> {
                        // Player 2 finished their test so show the results
                        player2ReactionTime = reactionTimeMs
                        player2Time.text = reactionTimeMs.toString()
                        battleState = BattleState.FINISHED
                        showBattleResult()
                    }
                    else -> {}
                }
            }
            else -> {}
        }
    }
    
    // Called when the user pressed the button too early
    private fun handleEarlyPress() {
        when (currentGameMode) {
            GameMode.SOLO -> {
                reactionTimeTextView.text = "Too early! Try again."
                startButton.isEnabled = true
            }
            GameMode.BATTLE -> {
                // Early press counts as 9999ms which is basically losing
                when (battleState) {
                    BattleState.PLAYER1_WAITING -> {
                        player1ReactionTime = 9999L
                        player1Time.text = "EARLY!"
                        battleState = BattleState.PLAYER2_TURN
                        battleStatus.text = "Player 1 pressed too early! Player 2's turn"
                        battleStartButton.text = "PLAYER 2 GO"
                        battleStartButton.isEnabled = true
                        turnIndicator.visibility = View.GONE
                    }
                    BattleState.PLAYER2_WAITING -> {
                        player2ReactionTime = 9999L
                        player2Time.text = "EARLY!"
                        battleState = BattleState.FINISHED
                        showBattleResult()
                    }
                    else -> {}
                }
            }
            else -> {}
        }
    }


    // BATTLE MODE METHODS
    // These handle the two player battle mode
    
    // Called when the battle button is clicked
    private fun handleBattleButtonClick() {
        when (battleState) {
            BattleState.IDLE -> {
                // Start player 1s turn
                battleState = BattleState.PLAYER1_TURN
                battleStatus.text = "Player 1 - Get ready!"
                battleStartButton.isEnabled = false
                turnIndicator.visibility = View.VISIBLE
                turnIndicator.text = "Get ready..."
                sendStartCommand()
                battleState = BattleState.PLAYER1_WAITING
            }
            BattleState.PLAYER2_TURN -> {
                // Start player 2s turn
                battleStatus.text = "Player 2 - Get ready!"
                battleStartButton.isEnabled = false
                turnIndicator.visibility = View.VISIBLE
                turnIndicator.text = "Get ready..."
                sendStartCommand()
                battleState = BattleState.PLAYER2_WAITING
            }
            BattleState.FINISHED -> resetBattle()
            else -> {}
        }
    }
    
    // Shows who won the battle
    private fun showBattleResult() {
        turnIndicator.visibility = View.GONE
        winnerText.visibility = View.VISIBLE
        
        val p1Time = player1ReactionTime ?: 9999L
        val p2Time = player2ReactionTime ?: 9999L
        
        // Lower time wins
        when {
            p1Time < p2Time -> {
                winnerText.text = "🏆 PLAYER 1 WINS! 🏆"
                winnerText.setTextColor(getColor(android.R.color.holo_blue_light))
            }
            p2Time < p1Time -> {
                winnerText.text = "🏆 PLAYER 2 WINS! 🏆"
                winnerText.setTextColor(getColor(android.R.color.holo_orange_light))
            }
            else -> {
                winnerText.text = "🤝 IT'S A TIE! 🤝"
                winnerText.setTextColor(getColor(android.R.color.white))
            }
        }
        
        battleStatus.text = "Difference: ${kotlin.math.abs(p1Time - p2Time)}ms"
        battleStartButton.text = "PLAY AGAIN"
        battleStartButton.isEnabled = true
    }
    
    // Resets the battle for a new game
    private fun resetBattle() {
        battleState = BattleState.IDLE
        player1ReactionTime = null
        player2ReactionTime = null
        player1Time.text = "--"
        player2Time.text = "--"
        winnerText.visibility = View.GONE
        turnIndicator.visibility = View.GONE
        battleStatus.text = "Press START to begin"
        battleStartButton.text = "START BATTLE"
        battleStartButton.isEnabled = bluetoothGatt != null
    }


    // MEMORY GAME METHODS
    // These handle the number memory game
    
    // Called when the memory button is clicked
    private fun handleMemoryButtonClick() {
        when (memoryState) {
            MemoryState.IDLE, MemoryState.RESULT -> {
                startMemoryRound()
            }
            MemoryState.INPUT -> {
                checkMemoryAnswer()
            }
            else -> {}
        }
    }
    
    // Starts a new memory round
    private fun startMemoryRound() {
        memoryState = MemoryState.SHOWING
        memoryStatus.text = "Watch the micro:bit!"
        memoryDisplay.text = "👀"
        memoryInputLayout.visibility = View.GONE
        memoryResult.visibility = View.GONE
        memorySubmitButton.isEnabled = false
        memorySubmitButton.text = "WATCHING..."
        
        // Tell micro:bit to show numbers for this level
        sendMemoryCommand(currentMemoryLevel)
    }
    
    // Called when we receive the numbers from the micro:bit
    private fun handleMemoryNumbersReceived(numbers: String) {
        currentNumbers = numbers
        Log.d("MainActivity", "Memory numbers to remember: $numbers")
    }
    
    // Called when the micro:bit is done showing numbers
    private fun handleMemoryDisplayDone() {
        memoryState = MemoryState.INPUT
        memoryStatus.text = "Enter the numbers!"
        memoryDisplay.text = "?"
        memoryInputLayout.visibility = View.VISIBLE
        memoryInput.text?.clear()
        memoryInput.requestFocus()
        memorySubmitButton.isEnabled = true
        memorySubmitButton.text = "SUBMIT"
        
        // Show the keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(memoryInput, InputMethodManager.SHOW_IMPLICIT)
    }
    
    // Checks if the users answer is correct
    private fun checkMemoryAnswer() {
        val userAnswer = memoryInput.text.toString().trim()
        
        // Hide keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(memoryInput.windowToken, 0)
        
        memoryState = MemoryState.RESULT
        memoryResult.visibility = View.VISIBLE
        
        Log.d("MainActivity", "Checking answer - User: '$userAnswer' vs Expected: '$currentNumbers'")
        Log.d("MainActivity", "User length: ${userAnswer.length}, Expected length: ${currentNumbers.length}")
        
        if (userAnswer == currentNumbers) {
            // Correct answer
            memoryResult.text = "✓ CORRECT!"
            memoryResult.setTextColor(getColor(android.R.color.holo_green_light))
            memoryDisplay.text = currentNumbers
            
            // Update best score
            if (currentMemoryLevel > memoryBestScore) {
                memoryBestScore = currentMemoryLevel
                memoryHighScore.text = "Best: $memoryBestScore digits"
            }
            
            // Go to next level
            currentMemoryLevel++
            memoryLevel.text = "Level: $currentMemoryLevel"
            memoryStatus.text = "Get ready for ${currentMemoryLevel} digits!"
            memorySubmitButton.text = "NEXT LEVEL"
        } else {
            // Wrong answer
            // Show what the user typed in the yellow display
            memoryDisplay.text = userAnswer
            // Show the correct answer in the red result text
            memoryResult.text = "✗ WRONG! It was: $currentNumbers"
            memoryResult.setTextColor(getColor(android.R.color.holo_red_light))
            
            // Save score to Firebase
            if (currentMemoryLevel > 1) {
                saveMemoryScoreToFirebase(currentMemoryLevel - 1)
            }
            
            // Reset to level 1
            currentMemoryLevel = 1
            memoryLevel.text = "Level: $currentMemoryLevel"
            memoryStatus.text = "Game Over! Try again?"
            memorySubmitButton.text = "TRY AGAIN"
        }
        
        memoryInputLayout.visibility = View.GONE
        memorySubmitButton.isEnabled = true
    }
    
    // Resets the memory game
    private fun resetMemoryGame() {
        memoryState = MemoryState.IDLE
        currentMemoryLevel = 1
        currentNumbers = ""
        memoryLevel.text = "Level: 1"
        memoryStatus.text = "Press START to begin"
        memoryDisplay.text = "?"
        memoryInputLayout.visibility = View.GONE
        memoryResult.visibility = View.GONE
        memorySubmitButton.text = "START"
        memorySubmitButton.isEnabled = bluetoothGatt != null
        memoryHighScore.text = "Best: $memoryBestScore digits"
    }


    // BLUETOOTH METHODS
    // These handle connecting and talking to the micro:bit

    // Called when the app comes back to the foreground
    override fun onResume() {
        super.onResume()
        if (hasPermissions() && bluetoothGatt == null && !isConnecting) {
            startBleScan()
        }
    }

    // Called when the app goes to the background
    override fun onPause() {
        super.onPause()
        stopBleScan()
    }

    // Called when the app is closed
    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.close()
    }

    // Checks if we have Bluetooth permissions
    private fun hasPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.all { ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    // Starts scanning for Bluetooth devices
    private fun startBleScan() {
        runOnUiThread { 
            connectionStatusTextView.text = "● Scanning..."
            connectionStatusTextView.setTextColor(getColor(android.R.color.holo_orange_light))
        }
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        bluetoothAdapter.bluetoothLeScanner.startScan(null, scanSettings, scanCallback)
    }

    // Stops scanning for Bluetooth devices
    private fun stopBleScan() {
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
    }

    // Connects to the micro:bit
    private fun connectToDevice(device: BluetoothDevice) {
        runOnUiThread { 
            connectionStatusTextView.text = "● Connecting..."
            connectionStatusTextView.setTextColor(getColor(android.R.color.holo_orange_light))
        }
        device.connectGatt(this, false, gattCallback)
    }

    // Sends a command to the micro:bit
    private fun sendCommand(command: String) {
        val gatt = bluetoothGatt ?: return
        val txChar = uartTxCharacteristic ?: return
        
        val data = "$command\n".toByteArray(Charsets.UTF_8)
        Log.d("MainActivity", "Sending: $command")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val writeType = if (txChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            gatt.writeCharacteristic(txChar, data, writeType)
        } else {
            @Suppress("DEPRECATION")
            txChar.writeType = if (txChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            @Suppress("DEPRECATION")
            txChar.value = data
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(txChar)
        }
    }

    // Sends a test message to check connection
    private fun sendWarmupPing() = sendCommand("PING")
    
    // Tells the micro:bit to start a reaction test
    private fun sendStartCommand() = sendCommand("START")
    
    // Tells the micro:bit to start a memory game round
    private fun sendMemoryCommand(level: Int) = sendCommand("MEMORY:$level")

    // Starts a solo reaction test
    private fun startReactionTest() {
        startButton.isEnabled = false
        reactionTimeTextView.text = "Get ready..."
        sendStartCommand()
    }
    
    // Called when the user shakes the micro:bit to restart
    private fun handleShakeRestart() {
        if (currentGameMode == GameMode.SOLO && startButton.isEnabled) {
            Log.d("MainActivity", "Shake restart triggered!")
            startReactionTest()
        }
    }


    // FIREBASE METHODS
    // These save and load data from the cloud database

    // Saves a reaction time to Firebase
    private fun saveReactionTimeToFirebase(reactionTimeMs: Long) {
        val data = hashMapOf(
            "reactionTimeMs" to reactionTimeMs,
            "timestamp" to System.currentTimeMillis(),
            "deviceId" to MICROBIT_ADDRESS
        )
        db.collection("reaction_times").add(data)
    }
    
    // Saves a memory game score to Firebase
    private fun saveMemoryScoreToFirebase(score: Int) {
        val data = hashMapOf(
            "score" to score,
            "timestamp" to System.currentTimeMillis(),
            "type" to "memory"
        )
        db.collection("memory_scores").add(data)
    }

    // Loads reaction time history from Firebase
    private fun loadHistory() {
        historyStatsText.text = "Loading..."
        
        db.collection("reaction_times")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { documents ->
                historyList.clear()
                var totalTime = 0L
                var bestTime = Long.MAX_VALUE
                
                for (document in documents) {
                    val reactionTimeMs = document.getLong("reactionTimeMs") ?: 0L
                    val timestamp = document.getLong("timestamp") ?: 0L
                    historyList.add(ReactionTimeEntry(reactionTimeMs, timestamp))
                    totalTime += reactionTimeMs
                    if (reactionTimeMs < bestTime) bestTime = reactionTimeMs
                }
                
                historyAdapter.notifyDataSetChanged()
                
                if (historyList.isNotEmpty()) {
                    val avgTime = totalTime / historyList.size
                    historyStatsText.text = "Best: ${bestTime}ms  •  Avg: ${avgTime}ms  •  Tests: ${historyList.size}"
                } else {
                    historyStatsText.text = "No tests yet. Go take some tests!"
                }
            }
            .addOnFailureListener {
                historyStatsText.text = "Error loading history"
            }
    }
}
