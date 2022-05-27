package com.nishantprojects.minesweeper

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.*
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout
import com.google.gson.Gson
import java.util.*
import kotlin.random.Random


class PlayGameActivity : AppCompatActivity() {

    private lateinit var boardConfig: BoardSize
    private var seconds = 0
    private var running = false
    private var openCellCount = 0
    private var remainingMines: Int = 0     //  To be displayed in TextView
    //  Optimisation to keep record of correctly marked mines so as to prevent O(n^2) looping to check if game is complete
    private var minesCorrectlyMarked = 0
    private var firstClick = true           //  To detect first click
    private lateinit var board: Array<Array<MineCell>>  //  Stores the board
    private lateinit var onClickPlayer: MediaPlayer     //  Sound on CLick
    private lateinit var onLongClickPlayer: MediaPlayer //  Sound on LongClick
    private lateinit var mineBlastSound: MediaPlayer    //  Sound if mine is blasted
    private lateinit var vibrator: Vibrator             //  Vibrate on LongPress
    private var status = Status.ONGOING                 //  Start the board with status as Ongoing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play_game)

        //  Receive board configuration from intent as JSON string
        boardConfig =
            Gson().fromJson(intent.getStringExtra(EXTRA_BOARD_CONFIG), BoardSize::class.java)
        setRowsColsMines()      //  Sets configuration to local variables
        remainingMines = boardConfig.mines      //  Initially, all mines are unmarked
        var idCounter = 1000    //  A counter to assign unique IDs to each button
        val gridLayout: GridLayout = findViewById(R.id.board_layout)
        gridLayout.columnCount = boardConfig.columns
        gridLayout.rowCount = boardConfig.rows

        //  For each row and column, set an clickable ImageView. Initially all unturned
        for (i in 0 until boardConfig.rows) {
            for (j in 0 until boardConfig.columns) {

                val imageView = ImageView(this)
                val rowSpec = GridLayout.spec(i)
                val colSpec = GridLayout.spec(j)
                val lp = GridLayout.LayoutParams(rowSpec, colSpec)
                imageView.layoutParams = lp
                imageView.setImageResource(R.drawable.unturned_icon)
                imageView.setPadding(100, 100, 100, 100)
                imageView.background = ContextCompat.getDrawable(this, R.drawable.grid_border)
                imageView.id = idCounter++
                imageView.setOnClickListener {
                    cellClicked(it)     //  On click listener to un-turn a cell
                }
                imageView.setOnLongClickListener {
                    cellLongClicked(it) //  On long click listener to flag a cell as mine
                }
                gridLayout.addView(imageView)

            }
        }
        //  Initialising our board for logic purpose
        board = Array(boardConfig.rows) {
            Array(boardConfig.columns) {
                MineCell()      //  A data class object to store information about each cell
            }
        }
        updateMineCountTextView()   //  A utility function to update remaining mine count in a text view
        runTimer()  //  A runnable to run time passed after starting game
        //  Sets sound  and vibration for button clicks
        onClickPlayer = MediaPlayer.create(this, R.raw.click_audio)
        onLongClickPlayer = MediaPlayer.create(this, R.raw.long_click_audio)
        mineBlastSound = MediaPlayer.create(this, R.raw.mine_blast_sound)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                this.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        //  Set Restart Button
        findViewById<Button>(R.id.restart_button).setOnClickListener {
            if (!firstClick || remainingMines != boardConfig.mines) {
                if (status == Status.ONGOING) {
                    AlertDialog.Builder(this).apply {
                        setTitle("Restart?")
                        setMessage("Your current progress would be Lost!!")
                        setPositiveButton("Confirm") { _, _ ->
                            resetConfiguration()
                        }
                        setNegativeButton("Cancel") { _, _ -> }
                    }.create().show()
                } else
                    resetConfiguration()

            }
        }

    }

    //  If game is running, confirm from user if back is pressed
    override fun onBackPressed() {
        if (status == Status.ONGOING && !firstClick) {
            AlertDialog.Builder(this).apply {
                setTitle("Are you sure want to quit?")
                setMessage("Current progress would be lost!!")
                setPositiveButton("Confirm") { _, _ ->
                    super.onBackPressed()
                }
                setNegativeButton("Cancel") { _, _ -> }
            }.create().show()
        } else
            super.onBackPressed()
    }

    //  Pause the timer if new Activity is opened or Phone is locked, etc.
    override fun onPause() {
        super.onPause()
        running = false
    }

    //  Resume the timer on Resuming the activity
    override fun onResume() {
        super.onResume()
        if (status == Status.ONGOING && !firstClick)
            running = true
    }

    private fun cellClicked(view: View) {
        if (status != Status.ONGOING)   //  If game is won or lost, no click accepted. Though listeners are removed upon win or loose
            return

        //  Getting row and column index from unique ID assigned
        val rowIndex = (view.id - 1000) / (boardConfig.columns)
        val colIndex = (view.id - 1000) % (boardConfig.columns)
        val cell = board[rowIndex][colIndex]
        if (firstClick) {
            running = true                  //  Start timer on first click
            setMines(rowIndex, colIndex)    //  Set mines after first click
        }
        firstClick = false
        if (cell.isMarked || cell.isRevealed) return    //  If cell is marked as flag or it is already revealed, no click response. Though listeners removed when revealed

        //  If a user hits a Mine
        if (cell.value == MINE) {
            status = Status.LOST
            cell.value = BLASTED_MINE
            mineBlastSound.start()
            running = false
            youLoose()
            revealAll()     //  Reveal all the cells of board upon hitting a mine
            return
        } else {    //  It is a valid cell and can be unturned
            onClickPlayer.start()
            reveal(rowIndex, colIndex)
        }
        if (isComplete() && status != Status.LOST) {    //  Check if game is complete
            status = Status.WON
            youWin()
        }

    }

    private fun cellLongClicked(view: View): Boolean {
        if (status != Status.ONGOING)       //  If game is won or lost, no click accepted. Though listeners are removed upon win or loose
            return true

        //  Getting row and column index from unique ID assigned
        val image = view as ImageView
        val rowIndex = (view.id - 1000) / (boardConfig.columns)
        val colIndex = (view.id - 1000) % (boardConfig.columns)
        val cell = board[rowIndex][colIndex]
        if (cell.isRevealed)    //  If cell is already revealed, it cannot be flagged as mine
            return true

        //  If a cell is already marked, un-mark it
        if (cell.isMarked) {
            image.setImageResource(R.drawable.unturned_icon)
            remainingMines++
            if (cell.value == MINE)      //  If it was a mine and user un-flags it, then number of correctly marked mines decreases by 1
                minesCorrectlyMarked--      //  Optimisation to keep record of correctly marked mines so as to prevent O(n^2) looping to check if game is complete
        } else {
            if (remainingMines > 0) {
//                firstClick = false
                running = true
                image.setImageResource(R.drawable.flag_icon)
                remainingMines--
                if (cell.value == MINE)      //  If it was a mine and user flags it correctly, then number of correctly marked mines increases by 1
                    minesCorrectlyMarked++      //  Optimisation to keep record of correctly marked mines so as to prevent O(n^2) looping to check if game is complete
            } else return true
        }
        cell.isMarked = !cell.isMarked      //  Invert the isMarked condition
        onLongClickPlayer.start()           //  Play sound effect
        vibrator.vibrate(VibrationEffect.createOneShot(50, 1))
        updateMineCountTextView()           //  Update the mine count in activity's text view
        if (isComplete() && status != Status.LOST) {    //  If game is complete, call the win function
            status = Status.WON
            youWin()
        }
        return true     //  true if the callback consumed the long click, false otherwise.
    }

    // Reset the complete configuration on button click
    private fun resetConfiguration() {
        seconds = 0
        running = false
        firstClick = true
        minesCorrectlyMarked = 0
        openCellCount = 0
        remainingMines = boardConfig.mines
        status = Status.ONGOING
        //  Set un-turned icon to each image view and clickListeners as they were removed when they were revealed
        for (i in 0 until boardConfig.rows) {
            for (j in 0 until boardConfig.columns) {
                val id = 1000 + (i * boardConfig.columns) + j
                val image = findViewById<ImageView>(id)
                image.setImageResource(R.drawable.unturned_icon)
                image.setOnClickListener {
                    cellClicked(it)
                }
                image.setOnLongClickListener {
                    cellLongClicked(it)
                }
            }
        }
        board = Array(boardConfig.rows) {
            Array(boardConfig.columns) {
                MineCell()
            }
        }
        updateMineCountTextView()
    }

    private fun setMines(x: Int, y: Int) {
        //  x and y are coordinates where the first click occurred.
        var count = 0
        while (count < boardConfig.mines) {
            //  Mines are set at random position using Random Number Generator
            val randomNumber = Random.nextInt(0, boardConfig.rows * boardConfig.columns)
            val rowIndex = randomNumber / (boardConfig.columns)
            val colIndex = randomNumber % (boardConfig.columns)
            if (rowIndex == x && colIndex == y) //  Skip this first click location
                continue
            val cell = board[rowIndex][colIndex]
            if (cell.value != MINE) {    //  Check if mine is not present already
                cell.value = MINE
                count++
                updateNeighbours(rowIndex, colIndex)    //  Update the neighbours where the mine is set.
            }
        }
    }

    private fun updateNeighbours(row: Int, column: Int) {
        //  Traverse in all 8 directions (if possible) and increase its count by 1
        for (i in movement) {
            for (j in movement) {
                if (((row + i) in 0 until boardConfig.rows) && ((column + j) in 0 until boardConfig.columns) &&
                    board[row + i][column + j].value != MINE
                )
                    board[row + i][column + j].value++
            }
        }
    }

    //  Reveal(un-turn)  a particular cell
    private fun reveal(x: Int, y: Int) {
        if (!board[x][y].isRevealed && !board[x][y].isMarked) {
            val id = 1000 + (x * boardConfig.columns) + y
            val image = findViewById<ImageView>(id)
            image.setOnClickListener(null)
            image.setOnLongClickListener(null)
            showIcon(x, y, image)
            board[x][y].isRevealed = true
            openCellCount++
            //  If it's value is 0, i.e, it doesn't have any mine nearby, then un-turn all it's neighbours as well
            if (board[x][y].value == 0) {
                for (i in movement)
                    for (j in movement)
                        if ((i != 0 || j != 0) && ((x + i) in 0 until boardConfig.rows) && ((y + j) in 0 until boardConfig.columns))
                            reveal(x + i, y + j)
            }
        }
    }

    //  Reveal(un-turn) all the cells upon hitting a mine
    private fun revealAll() {
        for (x in 0 until boardConfig.rows) {
            for (y in 0 until boardConfig.columns) {
                if (!board[x][y].isRevealed) {
                    val id = 1000 + (x * boardConfig.columns) + y
                    val image = findViewById<ImageView>(id)
                    image.setOnClickListener(null)
                    image.setOnLongClickListener(null)
                    showIcon(x, y, image)
                    board[x][y].isRevealed = true
                }
            }
        }
    }

    //  Update the icon of cell according to its current status
    private fun showIcon(x: Int, y: Int, image: ImageView) {
        val cell = board[x][y]
        if (cell.isMarked && !cell.isRevealed) {
            if (cell.value == MINE)
                image.setImageResource(R.drawable.flag_icon)
            else    //  Cell was wrongly marked as mine
                image.setImageResource(R.drawable.wrong_flag)
            return
        }
        when (cell.value) {
            BLASTED_MINE -> image.setImageResource(R.drawable.blasted_mine)
            MINE -> image.setImageResource(R.drawable.stable_mine)
            0 -> image.setImageResource(R.drawable.turned_icon)
            1 -> image.setImageResource(R.drawable.num1)
            2 -> image.setImageResource(R.drawable.num2)
            3 -> image.setImageResource(R.drawable.num3)
            4 -> image.setImageResource(R.drawable.num4)
            5 -> image.setImageResource(R.drawable.num5)
            6 -> image.setImageResource(R.drawable.num6)
            7 -> image.setImageResource(R.drawable.num7)
            8 -> image.setImageResource(R.drawable.num8)
        }
    }

    //  To evaluate win condition in O(1) time instead of looping with O(n^2)
    private fun isComplete(): Boolean {
        val minesMarked = (minesCorrectlyMarked == boardConfig.mines)
        val valuesRevealed =
            openCellCount == ((boardConfig.rows * boardConfig.columns) - boardConfig.mines)

        return minesMarked || valuesRevealed
    }

    //  Sets the remaining number of mines in a text view
    private fun updateMineCountTextView() {
        val text = "%03d".format(remainingMines)
        findViewById<TextView>(R.id.mines_count).text = text
    }

    //  win function called when game is complete
    private fun youWin() {
        running = false     //  Stops the timer
        disableAllButtons() //  Remove on click listeners on each button
        remainingMines = 0
        updateMineCountTextView()
        updateGameTimeStatistics(seconds)   //  Save the current time lapsed through SharedPreferences
        //  Opens a new activity to show that user has won
        val intentWin = Intent(this, WinActivity::class.java)
        intentWin.putExtra("DIFFICULTY", boardConfig.configuration)
        startActivity(intentWin)
        Toast.makeText(this, "Congratulations! You Won", Toast.LENGTH_SHORT).show()
    }

    //  loose function called when user hits mine
    private fun youLoose() {
        running = false     //  Stops the timer
        disableAllButtons() //  Remove on click listeners on each button
        Toast.makeText(this, "You Loose. Keep Trying!", Toast.LENGTH_SHORT).show()
    }

    //  Function to set best time and last game time in SharedPreferences
    private fun updateGameTimeStatistics(seconds: Int) {
        val sp = getSharedPreferences(MY_PREFERENCES, MODE_PRIVATE)
        var bestTime = sp.getInt(BEST_TIME, Int.MAX_VALUE)
        if (bestTime == 0)
            bestTime = Int.MAX_VALUE
        sp.edit().apply {
            putInt(LAST_GAME_TIME, seconds)
            if (seconds < bestTime) {
                putInt(BEST_TIME, seconds)
            }
            apply()
        }

    }

    //  Function to remove onClickListeners on each cell if win or loose
    private fun disableAllButtons() {
        for (i in 0 until boardConfig.rows) {
            for (j in 0 until boardConfig.columns) {
                val id = 1000 + (i * boardConfig.columns) + j
                val image = findViewById<ImageView>(id)
                image.setOnLongClickListener(null)
                image.setOnClickListener(null)
            }
        }
    }

    //  A runnable to execute the timer and sets current time elapsed in a text view
    private fun runTimer() {
        val timerTextView = findViewById<TextView>(R.id.time)
        val handler = Handler(Looper.myLooper()!!)

        handler.post(object : Runnable {
            override fun run() {
                timerTextView.text = getFormattedTime(seconds)
                if (running) {
                    seconds++
                }
                handler.postDelayed(this, 1000)
            }
        })

    }

    //  Sets number of rows, columns and mines from the intent received.
    private fun setRowsColsMines() {
        val config: String? = boardConfig.configuration
        if (config != "CUSTOM") {
            boardConfig.rows = 24
            boardConfig.columns = 14
        }
        when (config) {
            "BEGINNER" -> boardConfig.mines = 43
            "INTERMEDIATE" -> boardConfig.mines = 61
            "ADVANCED" -> boardConfig.mines = 108
        }
    }

    companion object {
        const val MINE = -1
        const val BLASTED_MINE = -2
        const val MY_PREFERENCES = "MY_PREFERENCES"
        const val LAST_GAME_TIME = "LAST_GAME_TIME"
        const val BEST_TIME = "BEST_TIME"
        const val EXTRA_BOARD_CONFIG = "com.nishantprojects.minesweeper.EXTRA.CONFIG"
        internal const val MAX_VOLUME: Double = 100.0
        val movement = intArrayOf(-1, 0, 1)
        fun getFormattedTime(seconds: Int): String {
            val minutes = seconds % 3600 / 60
            val secs = seconds % 60

            return java.lang.String
                .format(
                    Locale.getDefault(),
                    "%02d:%02d",
                    minutes, secs
                )
        }
    }


}