package com.nishantprojects.minesweeper

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nishantprojects.minesweeper.PlayGameActivity.Companion.BEST_TIME
import com.nishantprojects.minesweeper.PlayGameActivity.Companion.LAST_GAME_TIME
import com.nishantprojects.minesweeper.PlayGameActivity.Companion.MY_PREFERENCES
import com.nishantprojects.minesweeper.PlayGameActivity.Companion.getFormattedTime

class WinActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_win)

        //  Get difficulty level from intent and set in TextView
        val difficulty = intent.getStringExtra("DIFFICULTY")
        findViewById<TextView>(R.id.difficulty).text = difficulty

        //  Load game timings received from SharedPreferences into TextViews
        val sp = getSharedPreferences(MY_PREFERENCES, MODE_PRIVATE)
        val lastGameTime = "${getFormattedTime(sp.getInt(LAST_GAME_TIME, 0))} s"
        findViewById<TextView>(R.id.your_time).text = lastGameTime
        val bestTime = "${getFormattedTime(sp.getInt(BEST_TIME, 0))} s"
        findViewById<TextView>(R.id.best_time_win).text = bestTime

        //  Continue button which destroys this WinActivity
        findViewById<Button>(R.id.button).setOnClickListener { finish() }
    }
}