package com.example.v3

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
import java.util.UUID

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    // Solo mode views
    private lateinit var connectionStatusTextView: TextView
    private lateinit var reactionTimeTextView: TextView
    private lateinit var startButton: Button
    private lateinit var testScreen: View
    
    // Battle mode views
    private lateinit var battleScreen: View
    private lateinit var battleStartButton: Button
    private lateinit var battleStatus: TextView
    private lateinit var player1Time: TextView
    private lateinit var player2Time: TextView
    private lateinit var winnerText: TextView
    private lateinit var turnIndicator: TextView
    
    // Memory game views
    private lateinit var memoryScreen: View
    private lateinit var memoryLevel: TextView
    private lateinit var memoryStatus: TextView
    private lateinit var memoryDisplay: TextView
    private lateinit var memoryInputLayout: TextInputLayout
    private lateinit var memoryInput: TextInputEditText
    private lateinit var memoryResult: TextView
    private lateinit var memoryHighScore: TextView
    private lateinit var memorySubmitButton: Button
    
    // History views
    private lateinit var historyScreen: View
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyStatsText: TextView

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val microbitAddress = "C1:E8:3B:B1:F1:9B"
    private var bluetoothGatt: BluetoothGatt? = null
    private var isConnecting = false

    // Firebase Firestore
    private val db = FirebaseFirestore.getInstance()
    
    // History data
    private val historyList = mutableListOf<ReactionTimeEntry>()
    private lateinit var historyAdapter: HistoryAdapter

    // Game mode tracking
    private enum class GameMode { SOLO, BATTLE, MEMORY }
    private var currentGameMode = GameMode.SOLO
    
    // Battle mode state
    private enum class BattleState { IDLE, PLAYER1_TURN, PLAYER1_WAITING, PLAYER2_TURN, PLAYER2_WAITING, FINISHED }
    private var battleState = BattleState.IDLE
    private var player1ReactionTime: Long? = null
    private var player2ReactionTime: Long? = null
    
    // Memory game state
    private enum class MemoryState { IDLE, SHOWING, INPUT, RESULT }
    private var memoryState = MemoryState.IDLE
    private var currentMemoryLevel = 1
    private var currentNumbers = ""
    private var memoryBestScore = 0

    // micro:bit UART Service UUIDs
    private val UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val UART_TX_CHAR_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    private val UART_RX_CHAR_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var uartTxCharacteristic: BluetoothGattCharacteristic? = null
    private var uartRxCharacteristic: BluetoothGattCharacteristic? = null

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.entries.any { !it.value }) {
                // Handle permissions not granted
            } else {
                startBleScan()
            }
        }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (isConnecting) return
            
            if (result.device.address == microbitAddress || result.device.name?.contains("micro:bit") == true) {
                isConnecting = true
                stopBleScan()
                connectToDevice(result.device)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d("MainActivity", "Connection state changed: status=$status, newState=$newState")
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    bluetoothGatt = gatt
                    Log.d("MainActivity", "Connected to: ${gatt?.device?.address}")
                    runOnUiThread {
                        connectionStatusTextView.text = "● Connected"
                        connectionStatusTextView.setTextColor(getColor(android.R.color.holo_green_light))
                    }
                    gatt?.discoverServices()
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    bluetoothGatt = null
                    uartTxCharacteristic = null
                    uartRxCharacteristic = null
                    isConnecting = false
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

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.d("MainActivity", "Services discovered, status: $status")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val uartService = gatt?.getService(UART_SERVICE_UUID)
                if (uartService != null) {
                    Log.d("MainActivity", "UART service found!")
                    
                    uartTxCharacteristic = uartService.getCharacteristic(UART_TX_CHAR_UUID)
                    uartRxCharacteristic = uartService.getCharacteristic(UART_RX_CHAR_UUID)

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

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                sendWarmupPing()
            }
        }

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

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic?.uuid == UART_RX_CHAR_UUID) {
                @Suppress("DEPRECATION")
                val data = characteristic.value?.toString(Charsets.UTF_8)?.trim()
                Log.d("MainActivity", "Received: $data")

                data?.let {
                    when {
                        it.startsWith("RT:") -> {
                            val reactionTime = it.removePrefix("RT:")
                            val reactionTimeMs = reactionTime.toLongOrNull()
                            
                            runOnUiThread { handleReactionTimeReceived(reactionTimeMs ?: 0L) }
                            
                            if (currentGameMode == GameMode.SOLO) {
                                reactionTimeMs?.let { time -> saveReactionTimeToFirebase(time) }
                            }
                        }
                        it == "WAIT" -> {
                            runOnUiThread {
                                when (currentGameMode) {
                                    GameMode.SOLO -> reactionTimeTextView.text = "Wait for flash..."
                                    GameMode.BATTLE -> turnIndicator.text = "Wait for flash..."
                                    else -> {}
                                }
                            }
                        }
                        it == "EARLY" -> {
                            runOnUiThread { handleEarlyPress() }
                        }
                        it.startsWith("MEM:") -> {
                            // Memory game - numbers received from micro:bit
                            val numbers = it.removePrefix("MEM:").trim()
                            Log.d("MainActivity", "Memory numbers received: '$numbers' (length: ${numbers.length})")
                            runOnUiThread { handleMemoryNumbersReceived(numbers) }
                        }
                        it == "MEMDONE" -> {
                            // Memory game - display finished, time to input
                            runOnUiThread { handleMemoryDisplayDone() }
                        }
                        it == "SHAKE" -> {
                            // User shook the micro:bit to restart (accelerometer feature!)
                            Log.d("MainActivity", "Shake detected - restarting solo test")
                            runOnUiThread { handleShakeRestart() }
                        }
                        it == "PONG" -> {
                            Log.d("MainActivity", "Warmup complete")
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupNavigation()
        setupButtons()
        requestPermissions()
    }
    
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

        // Setup RecyclerView
        historyAdapter = HistoryAdapter(historyList)
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        historyRecyclerView.adapter = historyAdapter
    }
    
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
    
    private fun showScreen(screen: View) {
        testScreen.visibility = View.GONE
        battleScreen.visibility = View.GONE
        memoryScreen.visibility = View.GONE
        historyScreen.visibility = View.GONE
        screen.visibility = View.VISIBLE
    }
    
    private fun setupButtons() {
        startButton.isEnabled = false
        startButton.setOnClickListener { startReactionTest() }
        
        battleStartButton.isEnabled = false
        battleStartButton.setOnClickListener { handleBattleButtonClick() }
        
        memorySubmitButton.isEnabled = false
        memorySubmitButton.setOnClickListener { handleMemoryButtonClick() }
        
        // Memory input done action
        memoryInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (memoryState == MemoryState.INPUT) {
                    checkMemoryAnswer()
                }
                true
            } else false
        }
    }
    
    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestMultiplePermissions.launch(arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ))
        } else {
            requestMultiplePermissions.launch(arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            ))
        }
    }

    // ==================== REACTION TIME HANDLERS ====================
    
    private fun handleReactionTimeReceived(reactionTimeMs: Long) {
        when (currentGameMode) {
            GameMode.SOLO -> {
                reactionTimeTextView.text = "$reactionTimeMs ms"
                startButton.isEnabled = true
            }
            GameMode.BATTLE -> {
                when (battleState) {
                    BattleState.PLAYER1_WAITING -> {
                        player1ReactionTime = reactionTimeMs
                        player1Time.text = reactionTimeMs.toString()
                        battleState = BattleState.PLAYER2_TURN
                        battleStatus.text = "Player 1: ${reactionTimeMs}ms - Player 2's turn!"
                        battleStartButton.text = "PLAYER 2 GO"
                        battleStartButton.isEnabled = true
                        turnIndicator.visibility = View.GONE
                    }
                    BattleState.PLAYER2_WAITING -> {
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
    
    private fun handleEarlyPress() {
        when (currentGameMode) {
            GameMode.SOLO -> {
                reactionTimeTextView.text = "Too early! Try again."
                startButton.isEnabled = true
            }
            GameMode.BATTLE -> {
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

    // ==================== BATTLE MODE ====================
    
    private fun handleBattleButtonClick() {
        when (battleState) {
            BattleState.IDLE -> {
                battleState = BattleState.PLAYER1_TURN
                battleStatus.text = "Player 1 - Get ready!"
                battleStartButton.isEnabled = false
                turnIndicator.visibility = View.VISIBLE
                turnIndicator.text = "Get ready..."
                sendStartCommand()
                battleState = BattleState.PLAYER1_WAITING
            }
            BattleState.PLAYER2_TURN -> {
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
    
    private fun showBattleResult() {
        turnIndicator.visibility = View.GONE
        winnerText.visibility = View.VISIBLE
        
        val p1Time = player1ReactionTime ?: 9999L
        val p2Time = player2ReactionTime ?: 9999L
        
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

    // ==================== MEMORY GAME ====================
    
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
    
    private fun startMemoryRound() {
        memoryState = MemoryState.SHOWING
        memoryStatus.text = "Watch the micro:bit!"
        memoryDisplay.text = "👀"
        memoryInputLayout.visibility = View.GONE
        memoryResult.visibility = View.GONE
        memorySubmitButton.isEnabled = false
        memorySubmitButton.text = "WATCHING..."
        
        // Send command to micro:bit with current level
        sendMemoryCommand(currentMemoryLevel)
    }
    
    private fun handleMemoryNumbersReceived(numbers: String) {
        currentNumbers = numbers
        Log.d("MainActivity", "Memory numbers to remember: $numbers")
    }
    
    private fun handleMemoryDisplayDone() {
        memoryState = MemoryState.INPUT
        memoryStatus.text = "Enter the numbers!"
        memoryDisplay.text = "?"
        memoryInputLayout.visibility = View.VISIBLE
        memoryInput.text?.clear()
        memoryInput.requestFocus()
        memorySubmitButton.isEnabled = true
        memorySubmitButton.text = "SUBMIT"
        
        // Show keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(memoryInput, InputMethodManager.SHOW_IMPLICIT)
    }
    
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
            // Correct!
            memoryResult.text = "✓ CORRECT!"
            memoryResult.setTextColor(getColor(android.R.color.holo_green_light))
            memoryDisplay.text = currentNumbers
            
            // Update best score
            if (currentMemoryLevel > memoryBestScore) {
                memoryBestScore = currentMemoryLevel
                memoryHighScore.text = "Best: $memoryBestScore digits"
            }
            
            // Next level
            currentMemoryLevel++
            memoryLevel.text = "Level: $currentMemoryLevel"
            memoryStatus.text = "Get ready for ${currentMemoryLevel} digits!"
            memorySubmitButton.text = "NEXT LEVEL"
        } else {
            // Wrong!
            memoryResult.text = "✗ WRONG! It was: $currentNumbers"
            memoryResult.setTextColor(getColor(android.R.color.holo_red_light))
            memoryDisplay.text = currentNumbers
            
            // Save score to Firebase if it's good
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

    // ==================== BLE COMMUNICATION ====================

    override fun onResume() {
        super.onResume()
        if (hasPermissions() && bluetoothGatt == null && !isConnecting) {
            startBleScan()
        }
    }

    override fun onPause() {
        super.onPause()
        stopBleScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.close()
    }

    private fun hasPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.all { ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun startBleScan() {
        runOnUiThread { 
            connectionStatusTextView.text = "● Scanning..."
            connectionStatusTextView.setTextColor(getColor(android.R.color.holo_orange_light))
        }
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        bluetoothAdapter.bluetoothLeScanner.startScan(null, scanSettings, scanCallback)
    }

    private fun stopBleScan() {
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        runOnUiThread { 
            connectionStatusTextView.text = "● Connecting..."
            connectionStatusTextView.setTextColor(getColor(android.R.color.holo_orange_light))
        }
        device.connectGatt(this, false, gattCallback)
    }

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

    private fun sendWarmupPing() = sendCommand("PING")
    private fun sendStartCommand() = sendCommand("START")
    private fun sendMemoryCommand(level: Int) = sendCommand("MEMORY:$level")

    private fun startReactionTest() {
        startButton.isEnabled = false
        reactionTimeTextView.text = "Get ready..."
        sendStartCommand()
    }
    
    private fun handleShakeRestart() {
        // Only restart if we're in solo mode and button is currently enabled (game finished)
        if (currentGameMode == GameMode.SOLO && startButton.isEnabled) {
            Log.d("MainActivity", "Shake restart triggered!")
            startReactionTest()
        }
    }

    // ==================== FIREBASE ====================

    private fun saveReactionTimeToFirebase(reactionTimeMs: Long) {
        val data = hashMapOf(
            "reactionTimeMs" to reactionTimeMs,
            "timestamp" to System.currentTimeMillis(),
            "deviceId" to microbitAddress
        )
        db.collection("reaction_times").add(data)
    }
    
    private fun saveMemoryScoreToFirebase(score: Int) {
        val data = hashMapOf(
            "score" to score,
            "timestamp" to System.currentTimeMillis(),
            "type" to "memory"
        )
        db.collection("memory_scores").add(data)
    }

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
