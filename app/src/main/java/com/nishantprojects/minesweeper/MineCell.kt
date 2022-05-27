package com.nishantprojects.minesweeper

data class MineCell(
    var value: Int = 0,
    var isRevealed: Boolean = false,
    var isMarked: Boolean = false
)
