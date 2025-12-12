# Micro:bit Reaction Time + Memory Game
# 
# HOW TO USE:
# 1. Go to https://makecode.microbit.org/
# 2. Click "New Project"
# 3. Click the gear icon (settings) -> Extensions
# 4. Search for "bluetooth" and add it
# 5. Switch to Python mode (click "Python" at top)
# 6. Copy and paste this entire code
# 7. Download to your micro:bit
#
# IMPORTANT: In Project Settings, enable "No Pairing Required" for Bluetooth

def on_bluetooth_connected():
    basic.show_icon(IconNames.YES)

bluetooth.on_bluetooth_connected(on_bluetooth_connected)

def on_bluetooth_disconnected():
    basic.show_icon(IconNames.NO)

bluetooth.on_bluetooth_disconnected(on_bluetooth_disconnected)

# Variables
waiting_for_press = False
start_time = 0
early_check_active = False
game_finished = False  # Track if we just finished a game

def on_uart_data_received():
    global waiting_for_press, start_time, early_check_active, game_finished
    
    msg = bluetooth.uart_read_until(serial.delimiters(Delimiters.NEW_LINE))
    
    if msg == "PING":
        bluetooth.uart_write_string("PONG\n")
    elif msg == "START":
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

def run_reaction_test():
    global waiting_for_press, start_time, early_check_active, game_finished
    
    waiting_for_press = False
    early_check_active = False
    game_finished = False
    
    # Countdown 3, 2, 1
    basic.show_string("3")
    basic.pause(1000)
    basic.show_string("2")
    basic.pause(1000)
    basic.show_string("1")
    basic.pause(1000)
    basic.clear_screen()
    
    # Tell app to wait
    bluetooth.uart_write_string("WAIT\n")
    
    # Random delay 1-4 seconds
    delay = randint(1000, 4000)
    early_check_active = True
    basic.pause(delay)
    
    # Check if early press happened
    if early_check_active:
        early_check_active = False
        
        # Set up for button press BEFORE flashing
        start_time = input.running_time()
        waiting_for_press = True
        
        # Flash the screen
        basic.show_leds("""
            # # # # #
            # # # # #
            # # # # #
            # # # # #
            # # # # #
        """)
        
        # Send GO to app
        bluetooth.uart_write_string("GO\n")

def start_memory_game(num_digits: number):
    # Generate random digits and build string
    numbers = ""
    for i in range(num_digits):
        digit = randint(0, 9)
        numbers = numbers + convert_to_text(digit)
    
    # Send the numbers to app
    bluetooth.uart_write_string("MEM:" + numbers + "\n")
    
    # Brief pause before showing
    basic.pause(500)
    
    # Calculate display time based on level
    display_time = 1000
    if num_digits > 3:
        display_time = 800
    if num_digits > 5:
        display_time = 600
    if num_digits > 7:
        display_time = 500
    
    # Display each digit one at a time
    for i in range(num_digits):
        basic.show_string(numbers.char_at(i))
        basic.pause(display_time)
        basic.clear_screen()
        basic.pause(200)
    
    # Done showing - tell app
    bluetooth.uart_write_string("MEMDONE\n")
    basic.show_icon(IconNames.SMALL_SQUARE)

def on_button_pressed_a():
    global waiting_for_press, early_check_active, game_finished
    
    if early_check_active:
        early_check_active = False
        bluetooth.uart_write_string("EARLY\n")
        basic.show_icon(IconNames.NO)
        basic.pause(1000)
        basic.clear_screen()
        game_finished = True
    elif waiting_for_press:
        end_time = input.running_time()
        reaction_time = end_time - start_time
        waiting_for_press = False
        bluetooth.uart_write_string("RT:" + convert_to_text(reaction_time) + "\n")
        basic.show_icon(IconNames.YES)
        basic.pause(500)
        basic.clear_screen()
        game_finished = True

input.on_button_pressed(Button.A, on_button_pressed_a)

def on_button_pressed_b():
    global waiting_for_press, early_check_active, game_finished
    
    if early_check_active:
        early_check_active = False
        bluetooth.uart_write_string("EARLY\n")
        basic.show_icon(IconNames.NO)
        basic.pause(1000)
        basic.clear_screen()
        game_finished = True
    elif waiting_for_press:
        end_time = input.running_time()
        reaction_time = end_time - start_time
        waiting_for_press = False
        bluetooth.uart_write_string("RT:" + convert_to_text(reaction_time) + "\n")
        basic.show_icon(IconNames.YES)
        basic.pause(500)
        basic.clear_screen()
        game_finished = True

input.on_button_pressed(Button.B, on_button_pressed_b)

# Shake to restart - uses accelerometer!
# Custom shake detection with higher threshold
last_shake_time = 0

def check_for_shake():
    global game_finished, last_shake_time
    
    # Only check if game is finished
    if not game_finished:
        return
    
    # Get acceleration strength (absolute values combined)
    x = input.acceleration(Dimension.X)
    y = input.acceleration(Dimension.Y)
    z = input.acceleration(Dimension.Z)
    
    # Calculate total acceleration (simplified)
    total = abs(x) + abs(y) + abs(z)
    
    # Higher threshold = less sensitive (default shake is around 1000-1500)
    # Using 2500 for a more deliberate shake
    if total > 2500:
        current_time = input.running_time()
        # Prevent multiple triggers (500ms cooldown)
        if current_time - last_shake_time > 500:
            last_shake_time = current_time
            game_finished = False
            # Tell app we want to restart via shake
            bluetooth.uart_write_string("SHAKE\n")
            # Brief visual feedback
            basic.show_icon(IconNames.DIAMOND)
            basic.pause(800)
            basic.clear_screen()
            basic.pause(500)  # Extra delay before countdown starts

# Check for shake every 100ms in background
def on_forever():
    check_for_shake()
    basic.pause(100)

basic.forever(on_forever)

# Start UART service
bluetooth.start_uart_service()
basic.show_icon(IconNames.HEART)
