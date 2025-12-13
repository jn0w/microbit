package com.example.v3

// This file contains all the different game modes and states used in the app.
// Enums are like a list of fixed options that a variable can be.
// For example, GameMode can only be SOLO, BATTLE, or MEMORY, nothing else.


// The three different game modes the user can play.
// SOLO means single player reaction time test.
// BATTLE means two players take turns and compete.
// MEMORY means the number memory game.
enum class GameMode { 
    SOLO, 
    BATTLE, 
    MEMORY 
}


// All the possible states during a battle game.
// IDLE means waiting for someone to press start.
// PLAYER1_TURN means player 1 is about to take the test.
// PLAYER1_WAITING means player 1 is in the middle of their test.
// PLAYER2_TURN means player 2 is about to take the test.
// PLAYER2_WAITING means player 2 is in the middle of their test.
// FINISHED means both players are done and we show the winner.
enum class BattleState { 
    IDLE,
    PLAYER1_TURN,
    PLAYER1_WAITING,
    PLAYER2_TURN,
    PLAYER2_WAITING,
    FINISHED
}


// All the possible states during the memory game.
// IDLE means waiting for someone to press start.
// SHOWING means the micro:bit is displaying the numbers.
// INPUT means the user is typing their answer.
// RESULT means we are showing if they got it right or wrong.
enum class MemoryState { 
    IDLE,
    SHOWING,
    INPUT,
    RESULT
}


// This holds one reaction time record for the history list.
// reactionTimeMs is how fast the user reacted in milliseconds.
// timestamp is when this test happened stored as milliseconds since 1970.
data class ReactionTimeEntry(
    val reactionTimeMs: Long,
    val timestamp: Long
)

