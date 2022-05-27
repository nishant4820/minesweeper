package com.nishantprojects.minesweeper

import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.gson.Gson
import com.nishantprojects.minesweeper.PlayGameActivity.Companion.BEST_TIME
import com.nishantprojects.minesweeper.PlayGameActivity.Companion.EXTRA_BOARD_CONFIG
import com.nishantprojects.minesweeper.PlayGameActivity.Companion.LAST_GAME_TIME
import com.nishantprojects.minesweeper.PlayGameActivity.Companion.MAX_VOLUME
import com.nishantprojects.minesweeper.PlayGameActivity.Companion.MY_PREFERENCES
import com.nishantprojects.minesweeper.PlayGameActivity.Companion.getFormattedTime
import kotlin.math.ln

class MainActivity : AppCompatActivity() {

    private var gameLevel = BoardSize(null, 0, 0, 0)    //      To keep record of Board Configuration
    private var pressedTime: Long = 0       //  Variable to implement "Press back again to exit"
    private lateinit var mediaPlayer: MediaPlayer   //  Plays Background Music

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()            //  Hides action bar

        val title: ImageView = findViewById(R.id.imageView2)
        Glide.with(this).asGif().load(R.drawable.minesweeper_title_final)   //  Glide Library to Load GIF in a ImageView
            .listener(MyRequestListener())
            .into(title)

        findViewById<TextView>(R.id.select_difficulty_tv).apply {
            setOnClickListener { chooseLevel() }    //  Function call to choose from preset levels
        }

        findViewById<TextView>(R.id.custom_board_tv).apply {
            setOnClickListener { makeCustomBoard() }    //  Function call to set custom board
        }

        findViewById<Button>(R.id.start_button).apply {
            setOnClickListener { loadGame() }       //  Function call to start the game
        }

        setTimings()        //  Load the Statistics from SharedPreferences

        //  Setup Background Music
        mediaPlayer = MediaPlayer.create(this, R.raw.bg_music)
        val volume: Float = (1 - (ln(MAX_VOLUME - 20) / ln(MAX_VOLUME))).toFloat()
        mediaPlayer.setVolume(volume, volume)
        mediaPlayer.isLooping = true
        mediaPlayer.start()

        //  If battery is Low, if gives warning to close the app
        registerReceiver(BatteryLevelReceiver(), IntentFilter(Intent.ACTION_BATTERY_LOW))
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            this.registerReceiver(null, ifilter)
        }

        //  If battery falls below 5%, it will close the app within 5 seconds after giving Toast Warning
        val batteryPct: Float? = batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        }
        if (batteryPct != null) {
            if (batteryPct < 5f) {
                Toast.makeText(this, "Battery Low! Cannot play :(", Toast.LENGTH_LONG).show()
                val handler = Handler(Looper.myLooper()!!)
                handler.postDelayed({ finish() }, 5000)
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.stop()  //  Stop the BG Music if app is Closed.
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer.pause()     //  Pause the BG Music if new Activity is opened or Phone is locked, etc.
    }

    //  If MainActivity is resumed again, then play BG Music and set previous game configuration to null
    override fun onResume() {
        super.onResume()
        setTimings()
        mediaPlayer.start()
        gameLevel.configuration = null
    }

    //  Implemented "Press back again to exit" so as to avoid accidental back clicks
    override fun onBackPressed() {
        if (pressedTime + 2000 > System.currentTimeMillis()) {
            super.onBackPressed()
            finish()
        } else
            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()
        pressedTime = System.currentTimeMillis()
    }

    //  Function to set best time and last game time in home screen by loading from SharedPreferences
    private fun setTimings() {
        val sp = getSharedPreferences(MY_PREFERENCES, MODE_PRIVATE)
        val lastGameTime = sp.getInt(LAST_GAME_TIME, 0)
        val lastGameTimeString =
            if (lastGameTime == 0) "Last Game Time: N/A" else "Last Game Time: ${
                getFormattedTime(lastGameTime)
            }s"
        findViewById<TextView>(R.id.last_game_time).text = lastGameTimeString
        val bestTime = sp.getInt(BEST_TIME, 0)
        val bestTimeString =
            if (bestTime == 0) "Best Time: N/A" else "Best Time: ${getFormattedTime(bestTime)}s"
        findViewById<TextView>(R.id.best_time).text = bestTimeString
    }

    //  Function to help user choose between 3 preset game levels
    private fun chooseLevel() {
        val dialogView = layoutInflater.inflate(R.layout.game_level_dialog_view, null)
        val spinner: Spinner = dialogView.findViewById(R.id.spinner)    //  Spinner to set dropdown list of Levels
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.gameLevels)
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        AlertDialog.Builder(this@MainActivity).apply {
            setTitle("Select Difficulty")
            setView(dialogView)
            setPositiveButton("Ok") { _, _ ->
                val selectedLevel: String = spinner.selectedItem as String
                if (selectedLevel == "Choose…") {
                    gameLevel.configuration = null
                    Toast.makeText(this@MainActivity, "No Selection", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "$selectedLevel selected", Toast.LENGTH_SHORT)
                        .show()
                    gameLevel.configuration = selectedLevel
                }
            }
            setNegativeButton("Cancel") { _, _ ->
                gameLevel.configuration = null
            }
        }.create().show()
    }

    //  Function to make a custom board as user wants.
    private fun makeCustomBoard() {
        val dialogView = layoutInflater.inflate(R.layout.custom_board_dialog_view, null)
        val rowEditText = dialogView.findViewById<EditText>(R.id.rowInput)
        val columnEditText = dialogView.findViewById<EditText>(R.id.columnInput)
        val mineEditText = dialogView.findViewById<EditText>(R.id.minesInput)

        //  Error listener to avoid empty fields
        setErrorListener(rowEditText)
        setErrorListener(columnEditText)
        setErrorListener(mineEditText)

        //  AlertDialog to take inputs of Custom Board
        AlertDialog.Builder(this).apply {
            setTitle("Enter Board Configuration")
            setView(dialogView)     // Setting a custom layout for dialog
            setPositiveButton("Set") { _, _ ->
                val rowInput = rowEditText.text.toString()
                val columnInput = columnEditText.text.toString()
                val minesInput = mineEditText.text.toString()
                if (rowInput.isNotBlank() && columnInput.isNotBlank() && minesInput.isNotBlank()) {
                    val rowInputInteger = rowInput.toInt()
                    val colInputInteger = columnInput.toInt()
                    val mineInputInteger = minesInput.toInt()

                    //  Some Constraints on Custom Board Input
                    if (colInputInteger > rowInputInteger) {   //  rows should be more than columns to not compromise with appearance
                        Toast.makeText(this@MainActivity, "Rows should be more than columns", Toast.LENGTH_LONG)
                            .show()
                        gameLevel.configuration = null
                    }
                    else if (rowInputInteger < 5 || colInputInteger < 5) {  //  Very few rows or columns
                        Toast.makeText(this@MainActivity, "Use more Rows/Columns", Toast.LENGTH_SHORT)
                            .show()
                        gameLevel.configuration = null
                    }
                    else if (rowInputInteger > (3*colInputInteger)) {   //  To avoid too much height of grid for better appearance
                        Toast.makeText(this@MainActivity, "Board Height Too Long!!", Toast.LENGTH_SHORT)
                            .show()
                        gameLevel.configuration = null
                    }
                    else if (mineInputInteger > (rowInputInteger * colInputInteger) / 4) {   //  Mines should be less than 1/4th of total cells
                        Toast.makeText(this@MainActivity, "Use Less Mines :)", Toast.LENGTH_SHORT)
                            .show()
                        gameLevel.configuration = null
                    }
                    else if (mineInputInteger < 5) {    //  Mines are too less
                        Toast.makeText(this@MainActivity, "Use More Mines :)", Toast.LENGTH_SHORT)
                            .show()
                        gameLevel.configuration = null
                    }
                    else {      //  If all conditions are satisfied, then set the board configuration acc to user input
                        with(gameLevel) {
                            configuration = "CUSTOM"
                            rows = rowInput.toInt()
                            columns = columnInput.toInt()
                            mines = minesInput.toInt()
                        }
                        Toast.makeText(
                            this@MainActivity,
                            "Custom Board Selected",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {    //  Some field is empty, cannot set board configuration
                    Toast.makeText(this@MainActivity, "Invalid Input", Toast.LENGTH_SHORT).show()
                    gameLevel.configuration = null
                }
            }
            setNegativeButton("Cancel") { _, _ ->
                gameLevel.configuration = null
            }
        }.create().show()

    }

    //  Error listener to avoid empty input fields
    private fun setErrorListener(editText: EditText) {
        editText.error =
            if (editText.text.toString().isNotEmpty()) null else "Field Cannot be Empty"
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                editText.error =
                    if (editText.text.toString().isNotEmpty()) null else "Field Cannot be Empty"
            }
        })
    }

    //  Start the Game Activity and passing configuration as EXTRA through JSON and Gson
    private fun loadGame() {
        if (gameLevel.configuration == null) {
            showEmptyConfigDialog()     //  If no game level is selected, show an empty warning alert dialog
        } else {
            val boardConfig: String = Gson().toJson(gameLevel)
            val intent = Intent(this, PlayGameActivity::class.java)
            intent.putExtra(EXTRA_BOARD_CONFIG, boardConfig)
            startActivity(intent)
        }
    }

    //  If no game level is selected, it shows an empty warning alert dialog
    private fun showEmptyConfigDialog() {
        AlertDialog.Builder(this).apply {
            setTitle("No Level Selected")
            setMessage("Do you want to start game with Beginner Level?")
            setPositiveButton("Yes") { _, _ ->
                gameLevel.configuration = "BEGINNER"
                loadGame()
            }
            setNegativeButton("Cancel") { _, _ -> }
        }.create().show()
    }

}

//  A RequestListener for Glide Library to set loop count of GIF as 1
class MyRequestListener : RequestListener<GifDrawable> {

    override fun onLoadFailed(
        e: GlideException?,
        model: Any?,
        target: Target<GifDrawable>?,
        isFirstResource: Boolean
    ): Boolean {
        return true
    }

    override fun onResourceReady(
        resource: GifDrawable?,
        model: Any?,
        target: Target<GifDrawable>?,
        dataSource: DataSource?,
        isFirstResource: Boolean
    ): Boolean {
        resource?.setLoopCount(1)   //  Setting loop count 1 to play GIF only once
        return false
    }
}