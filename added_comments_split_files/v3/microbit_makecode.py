

# BLUETOOTH CONNECTION HANDLERS
# These run automatically when the phone connects or disconnects

def on_bluetooth_connected():
    # Show a checkmark when the phone connects
    basic.show_icon(IconNames.YES)

bluetooth.on_bluetooth_connected(on_bluetooth_connected)

def on_bluetooth_disconnected():
    # Show an X when the phone disconnects
    basic.show_icon(IconNames.NO)

bluetooth.on_bluetooth_disconnected(on_bluetooth_disconnected)


# GAME STATE VARIABLES
# These keep track of what is happening in the game

# True when we are waiting for the user to press a button after the flash
waiting_for_press = False

# The time in milliseconds when the screen flashed
start_time = 0

# True during the random delay before the flash when pressing is too early
early_check_active = False

# True when a game just finished so shaking can restart it
game_finished = False


# MESSAGE HANDLER
# This runs when the Android app sends us a message

def on_uart_data_received():
    global waiting_for_press, start_time, early_check_active, game_finished
    
    # Read the message that was sent
    msg = bluetooth.uart_read_until(serial.delimiters(Delimiters.NEW_LINE))
    
    # Check what command was sent and do the right thing
    if msg == "PING":
        # PING is a test message so we respond with PONG
        bluetooth.uart_write_string("PONG\n")
    elif msg == "START":
        # START begins a new reaction time test
        game_finished = False
        run_reaction_test()
    elif msg == "MEMORY:1":
        start_memory_game(1)
    elif msg == "MEMORY:2":
        start_memory_game(2)
    elif msg == "MEMORY:3":
        start_memory_game(3)
    elif msg == "MEMORY:4":
        start_memory_game(4)
    elif msg == "MEMORY:5":
        start_memory_game(5)
    elif msg == "MEMORY:6":
        start_memory_game(6)
    elif msg == "MEMORY:7":
        start_memory_game(7)
    elif msg == "MEMORY:8":
        start_memory_game(8)
    elif msg == "MEMORY:9":
        start_memory_game(9)
    elif msg == "MEMORY:10":
        start_memory_game(10)
    elif msg == "MEMORY:11":
        start_memory_game(11)
    elif msg == "MEMORY:12":
        start_memory_game(12)

bluetooth.on_uart_data_received(serial.delimiters(Delimiters.NEW_LINE), on_uart_data_received)


# REACTION TIME TEST
# This runs the reaction time test
# 1. Shows countdown 3 2 1
# 2. Waits a random time between 1 and 4 seconds
# 3. Flashes the screen
# 4. Measures how long until user presses a button

def run_reaction_test():
    global waiting_for_press, start_time, early_check_active, game_finished
    
    # Reset all the state variables
    waiting_for_press = False
    early_check_active = False
    game_finished = False
    
    # Show countdown 3 2 1
    basic.show_string("3")
    basic.pause(1000)
    basic.show_string("2")
    basic.pause(1000)
    basic.show_string("1")
    basic.pause(1000)
    basic.clear_screen()
    
    # Tell the app we are now waiting
    bluetooth.uart_write_string("WAIT\n")
    
    # Wait a random amount of time between 1 and 4 seconds
    # During this time pressing the button is too early
    delay = randint(1000, 4000)
    early_check_active = True
    basic.pause(delay)
    
    # If no early press happened then flash the screen
    if early_check_active:
        early_check_active = False
        
        # Record the time right before flashing
        start_time = input.running_time()
        waiting_for_press = True
        
        # Flash all LEDs on
        basic.show_leds("""
            # # # # #
            # # # # #
            # # # # #
            # # # # #
            # # # # #
        """)
        
        # Tell the app the flash happened
        bluetooth.uart_write_string("GO\n")


# MEMORY GAME
# This runs the memory game
# 1. Generates random numbers based on the level
# 2. Sends the numbers to the app
# 3. Shows each number one at a time on the screen
# 4. Tells the app when done so user can type their answer

def start_memory_game(num_digits: number):
    # Generate random digits and build a string like 472
    numbers = ""
    for i in range(num_digits):
        digit = randint(0, 9)
        numbers = numbers + convert_to_text(digit)
    
    # Send the numbers to the app so it knows the answer
    bluetooth.uart_write_string("MEM:" + numbers + "\n")
    
    # Wait a moment before showing
    basic.pause(500)
    
    # Higher levels show digits faster
    display_time = 1000
    if num_digits > 3:
        display_time = 800
    if num_digits > 5:
        display_time = 600
    if num_digits > 7:
        display_time = 500
    
    # Show each digit one at a time
    for i in range(num_digits):
        basic.show_string(numbers.char_at(i))
        basic.pause(display_time)
        basic.clear_screen()
        basic.pause(200)
    
    # Tell the app we are done showing numbers
    bluetooth.uart_write_string("MEMDONE\n")
    basic.show_icon(IconNames.SMALL_SQUARE)


# BUTTON A HANDLER
# This runs when the user presses button A

def on_button_pressed_a():
    global waiting_for_press, early_check_active, game_finished
    
    if early_check_active:
        # User pressed too early before the flash
        early_check_active = False
        bluetooth.uart_write_string("EARLY\n")
        basic.show_icon(IconNames.NO)
        basic.pause(1000)
        basic.clear_screen()
        game_finished = True
        
    elif waiting_for_press:
        # User pressed after the flash so calculate reaction time
        end_time = input.running_time()
        reaction_time = end_time - start_time
        waiting_for_press = False
        bluetooth.uart_write_string("RT:" + convert_to_text(reaction_time) + "\n")
        basic.show_icon(IconNames.YES)
        basic.pause(500)
        basic.clear_screen()
        game_finished = True

input.on_button_pressed(Button.A, on_button_pressed_a)


# BUTTON B HANDLER
# This runs when the user presses button B
# Works exactly the same as button A

def on_button_pressed_b():
    global waiting_for_press, early_check_active, game_finished
    
    if early_check_active:
        # User pressed too early before the flash
        early_check_active = False
        bluetooth.uart_write_string("EARLY\n")
        basic.show_icon(IconNames.NO)
        basic.pause(1000)
        basic.clear_screen()
        game_finished = True
        
    elif waiting_for_press:
        # User pressed after the flash so calculate reaction time
        end_time = input.running_time()
        reaction_time = end_time - start_time
        waiting_for_press = False
        bluetooth.uart_write_string("RT:" + convert_to_text(reaction_time) + "\n")
        basic.show_icon(IconNames.YES)
        basic.pause(500)
        basic.clear_screen()
        game_finished = True

input.on_button_pressed(Button.B, on_button_pressed_b)


# SHAKE TO RESTART
# The micro:bit has an accelerometer that detects shaking
# If you shake it after a game ends it will restart the test

last_shake_time = 0

def check_for_shake():
    global game_finished, last_shake_time
    
    # Only check for shakes when a game just finished
    if not game_finished:
        return
    
    # Read how much the micro:bit is moving
    x = input.acceleration(Dimension.X)
    y = input.acceleration(Dimension.Y)
    z = input.acceleration(Dimension.Z)
    
    # Add up all the movement
    total = abs(x) + abs(y) + abs(z)
    
    # If movement is strong enough its a shake
    if total > 2500:
        current_time = input.running_time()
        # Make sure 500ms passed since last shake
        if current_time - last_shake_time > 500:
            last_shake_time = current_time
            game_finished = False
            # Tell the app the user shook to restart
            bluetooth.uart_write_string("SHAKE\n")
            basic.show_icon(IconNames.DIAMOND)
            basic.pause(800)
            basic.clear_screen()
            basic.pause(500)


# MAIN LOOP
# This runs forever in the background checking for shakes

def on_forever():
    check_for_shake()
    basic.pause(100)

basic.forever(on_forever)


# STARTUP
# This runs once when the micro:bit turns on

# Start the Bluetooth messaging service
bluetooth.start_uart_service()

# Show a heart to indicate we are ready
basic.show_icon(IconNames.HEART)
